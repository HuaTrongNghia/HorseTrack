package com.horsetrack.equinecare.modules.nutrition.service;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.inventory.entity.MaterialRequisition;
import com.horsetrack.equinecare.modules.inventory.repository.MaterialRequisitionRepository;
import com.horsetrack.equinecare.modules.inventory.service.StockLedgerService;
import com.horsetrack.equinecare.modules.notification.service.AppNotificationService;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlan;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phiếu cấp thức ăn theo ngày của thực đơn (dùng bảng Material_Requisitions, mỗi món mỗi ngày một dòng).
 * Lý do của phiếu bắt đầu bằng "KP-planId-yyyyMMdd:" để tra lại (không dùng [ ] vì là ký tự đặc biệt của LIKE).
 * - Kho cấp đủ các món của một ngày thì Groom mới nhận việc cho ăn ngày đó; Groom hoàn thành chỉ ghi chi phí, không trừ kho lần nữa.
 * - Thực đơn bị hủy / sửa / thay thế / kết thúc: phiếu chưa cấp chuyển CANCELLED; phiếu đã cấp nhập lại kho phần chưa dùng rồi chuyển CANCELLED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NutritionRequisitions {

    public static final List<String> PENDING = List.of("SUBMITTED", "CHECKING_STOCK", "WAITING_RESTOCK");
    public static final List<String> ISSUED = List.of("ISSUED", "CLOSED");
    public static final List<String> DEAD = List.of("CANCELLED", "REJECTED");
    public static final Pattern MARKER = Pattern.compile("^KP-(\\d+)-(\\d{8}):");
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MaterialRequisitionRepository requisitionRepository;
    private final StockLedgerService stockLedger;
    private final UserRepository userRepository;
    private final AppNotificationService notifications;
    private final EntityManager entityManager;

    public static String marker(Integer planId, LocalDate day) {
        return "KP-" + planId + "-" + day.format(DAY) + ":";
    }

    public static boolean isNutrition(MaterialRequisition r) {
        return r.getReason() != null && MARKER.matcher(r.getReason()).find();
    }

    /** Phiếu còn hiệu lực (chưa hủy / từ chối) của thực đơn trong một ngày */
    @Transactional(readOnly = true)
    public List<MaterialRequisition> liveForDay(Integer planId, LocalDate day) {
        return requisitionRepository.findByReasonStartingWithOrderByRequisitionIdAsc(marker(planId, day)).stream()
                .filter(r -> !DEAD.contains(r.getStatus())).toList();
    }

    /** Kho đã cấp đủ thức ăn của ngày này (có phiếu và mọi phiếu còn hiệu lực đã cấp) */
    @Transactional(readOnly = true)
    public boolean isDayIssued(Integer planId, LocalDate day) {
        List<MaterialRequisition> live = liveForDay(planId, day);
        return !live.isEmpty() && live.stream().allMatch(r -> ISSUED.contains(r.getStatus()));
    }

    /**
     * Thực đơn bị hủy / sửa / thay thế / kết thúc: xử lý phiếu từ ngày fromDate trở đi.
     * Trả về mô tả ngắn (số phiếu hủy, lượng nhập lại kho) để ghi chú / báo kho; rỗng nếu không có phiếu nào.
     */
    @Transactional
    public String cancelForPlan(NutritionPlan plan, String reason, LocalDate fromDate) {
        LocalDate from = fromDate.isBefore(LocalDate.now()) ? LocalDate.now() : fromDate;
        int cancelled = 0;
        List<String> returned = new ArrayList<>();
        for (MaterialRequisition r : requisitionRepository.findByReasonStartingWithOrderByRequisitionIdAsc("KP-" + plan.getPlanId() + "-")) {
            Matcher m = MARKER.matcher(r.getReason());
            if (!m.find() || DEAD.contains(r.getStatus())) {
                continue;
            }
            LocalDate day = LocalDate.parse(m.group(2), DAY);
            if (day.isBefore(from)) {
                continue;
            }
            String note = " | Hủy: " + reason;
            if (ISSUED.contains(r.getStatus())) {
                // Phần chưa dùng = đã cấp - đã dùng (chi phí vật tư các việc cho ăn đã hoàn thành) - đã nhập lại (bữa tự hủy do trễ)
                double used = usedQuantity(plan.getPlanId(), day, r.getItem().getItemId());
                double back = StockLedgerService.round2((r.getIssuedQuantity() != null ? r.getIssuedQuantity() : r.getQuantity()) - used);
                if (back > 0) {
                    stockLedger.returnStock(r.getItem().getItemId(), back, "REQUISITION", r.getRequisitionId(),
                            "Nhập lại thức ăn chưa dùng: " + r.getRequisitionCode() + " (thực đơn #" + plan.getPlanId() + " " + reason + ")");
                    returned.add(StockLedgerService.fmt(back) + " " + r.getItem().getUnit() + " " + r.getItem().getItemName());
                    note += ", nhập lại kho " + StockLedgerService.fmt(back) + " " + r.getItem().getUnit();
                }
            }
            r.setStatus("CANCELLED");
            String text = r.getReason() + note;
            r.setReason(text.length() > 500 ? text.substring(0, 500) : text);
            requisitionRepository.save(r);
            cancelled++;
        }
        if (cancelled == 0) {
            return "";
        }
        String summary = "hủy " + cancelled + " phiếu cấp thức ăn" + (returned.isEmpty() ? "" : ", nhập lại kho " + String.join("; ", returned));
        notifyWarehouse("NUTRITION_STOCK", "Thực đơn #" + plan.getPlanId() + " (ngựa " + plan.getHorse().getHorseName() + ") " + reason,
                "Kho tự động " + summary + ".");
        return summary;
    }

    /** Lượng vật tư đã dùng (Task_Material_Usage của việc cho ăn đã hoàn thành) cộng lượng đã nhập lại khi bữa tự hủy do trễ, của thực đơn trong ngày */
    private double usedQuantity(Integer planId, LocalDate day, Integer itemId) {
        Object v = entityManager.createNativeQuery("SELECT COALESCE(SUM(u.quantity_used), 0) FROM Task_Material_Usage u JOIN Daily_Tasks t ON t.task_id = u.task_id "
                        + "WHERE t.notes LIKE ?1 AND t.task_date = ?2 AND u.item_id = ?3")
                .setParameter(1, "KP-" + planId + "-%").setParameter(2, day).setParameter(3, itemId).getSingleResult();
        Object r = entityManager.createNativeQuery("SELECT COALESCE(SUM(s.quantity), 0) FROM Stock_Transactions s JOIN Daily_Tasks t ON t.task_id = s.ref_id "
                        + "WHERE s.tx_type = 'RETURN' AND s.ref_type = 'TASK' AND t.notes LIKE ?1 AND t.task_date = ?2 AND s.item_id = ?3")
                .setParameter(1, "KP-" + planId + "-%").setParameter(2, day).setParameter(3, itemId).getSingleResult();
        return (v == null ? 0 : ((Number) v).doubleValue()) + (r == null ? 0 : ((Number) r).doubleValue());
    }

    public void notifyWarehouse(String purpose, String title, String message) {
        List<User> keepers = userRepository.findByRoleNameInAndIsActiveTrue(List.of("Warehouse Manager"));
        notifications.pushAll(keepers, purpose, title, message, "/warehouse/nutrition");
    }
}
