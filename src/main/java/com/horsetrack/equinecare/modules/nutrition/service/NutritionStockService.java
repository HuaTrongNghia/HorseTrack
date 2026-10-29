package com.horsetrack.equinecare.modules.nutrition.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.entity.MaterialRequisition;
import com.horsetrack.equinecare.modules.inventory.repository.MaterialRequisitionRepository;
import com.horsetrack.equinecare.modules.inventory.service.StockLedgerService;
import com.horsetrack.equinecare.modules.notification.service.AppNotificationService;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionStockRequestDTO;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlan;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlanDetail;
import com.horsetrack.equinecare.modules.nutrition.repository.NutritionPlanRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * Kho duyệt và cấp thức ăn cho thực đơn theo từng ngày.
 * - HLV / bác sĩ lập hoặc sửa thực đơn: sinh phiếu cấp thức ăn cho hôm nay (các bữa chưa tới giờ) và ngày mai, báo kho.
 * - Hằng ngày 00:02: sinh phiếu cho hôm nay và ngày mai của mọi thực đơn đang áp dụng.
 * - Kho cấp phiếu của một ngày: trừ kho rồi giao việc cho ăn ngày đó cho Groom.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NutritionStockService {

    private final MaterialRequisitionRepository requisitionRepository;
    private final NutritionPlanRepository planRepository;
    private final NutritionRequisitions requisitions;
    private final NutritionFeedTaskService feedTaskService;
    private final StockLedgerService stockLedger;
    private final AppNotificationService notifications;
    private final AuditTrail auditTrail;
    private final EntityManager entityManager;

    /** Lập / sửa thực đơn: phiếu cấp thức ăn hôm nay + ngày mai, báo kho. Trả về số phiếu vừa tạo. */
    @Transactional
    public int requestUpcoming(NutritionPlan plan) {
        LocalDate today = LocalDate.now();
        int n = requestForDay(plan, today, true) + requestForDay(plan, today.plusDays(1), false);
        if (n > 0) {
            requisitions.notifyWarehouse("NUTRITION_STOCK", "Cần cấp thức ăn: thực đơn #" + plan.getPlanId() + " - ngựa " + plan.getHorse().getHorseName(),
                    ("MEDICAL".equals(plan.getPlanType()) ? "Thực đơn y tế" : "Thực đơn") + " do " + plan.getCreatedBy().getFullName()
                            + " lập: " + n + " phiếu cấp thức ăn chờ duyệt (hôm nay và ngày mai).");
        }
        return n;
    }

    /** Hằng ngày 00:02: phiếu cấp thức ăn hôm nay và ngày mai cho mọi thực đơn đang áp dụng */
    @Scheduled(cron = "${app.nutrition.stock-request-cron:0 2 0 * * *}")
    @Transactional
    public void requestDaily() {
        LocalDate today = LocalDate.now();
        int n = 0;
        for (NutritionPlan p : planRepository.findByStatusOrderByCreatedDateDesc("ACTIVE")) {
            n += requestForDay(p, today, false) + requestForDay(p, today.plusDays(1), false);
        }
        if (n > 0) {
            requisitions.notifyWarehouse("NUTRITION_STOCK", "Phiếu cấp thức ăn mới", n + " phiếu cấp thức ăn (hôm nay và ngày mai) chờ kho duyệt.");
            log.info("Đã tạo {} phiếu cấp thức ăn", n);
        }
    }

    /** Phiếu cấp thức ăn một ngày: mỗi món một phiếu, số lượng = tổng các bữa trong ngày (hôm nay chỉ tính bữa chưa tới giờ nếu onlyUpcoming) */
    @Transactional
    public int requestForDay(NutritionPlan plan, LocalDate day, boolean onlyUpcoming) {
        if (!"ACTIVE".equals(plan.getStatus()) || day.isBefore(plan.getStartDate()) || day.isAfter(plan.getEndDate())
                || plan.getHorse().getStall() == null || !requisitions.liveForDay(plan.getPlanId(), day).isEmpty()) {
            return 0;
        }
        boolean today = day.equals(LocalDate.now());
        LocalTime now = LocalTime.now();
        Map<Integer, double[]> qty = new LinkedHashMap<>();
        Map<Integer, InventoryItem> items = new HashMap<>();
        for (NutritionPlanDetail d : plan.getDetails()) {
            if (d.getInventoryItem() == null || (onlyUpcoming && today && feedTaskService.mealTime(d.getMealType()).isBefore(now))) {
                continue;
            }
            qty.computeIfAbsent(d.getInventoryItem().getItemId(), k -> new double[1])[0] += d.getQuantity();
            items.put(d.getInventoryItem().getItemId(), d.getInventoryItem());
        }
        int created = 0;
        for (Map.Entry<Integer, double[]> e : qty.entrySet()) {
            double q = StockLedgerService.round2(e.getValue()[0]);
            if (q <= 0) {
                continue;
            }
            String reason = NutritionRequisitions.marker(plan.getPlanId(), day) + " Thức ăn ngày " + day.format(NutritionRequisitions.VN_DATE)
                    + " cho ngựa " + plan.getHorse().getHorseName() + " - thực đơn #" + plan.getPlanId()
                    + ("MEDICAL".equals(plan.getPlanType()) ? " (y tế)" : "");
            requisitionRepository.save(MaterialRequisition.builder()
                    .requestedBy(plan.getCreatedBy())
                    .barnZone(plan.getHorse().getStall().getBarnZone())
                    .item(items.get(e.getKey()))
                    .quantity(q)
                    .reason(reason.length() > 500 ? reason.substring(0, 500) : reason)
                    .requestedAt(LocalDateTime.now())
                    .neededBy(day)
                    .status("SUBMITTED")
                    .build());
            created++;
        }
        return created;
    }

    /** Kho: danh sách phiếu theo tab pending (chờ cấp phát) / issued (đã cấp phát) / cancelled (đã hủy / thay đổi), gom theo thực đơn + ngày */
    @Transactional(readOnly = true)
    public List<NutritionStockRequestDTO> list(String tab, LocalDate date) {
        String t = StringUtils.hasText(tab) ? tab.trim().toLowerCase() : "pending";
        if (!Set.of("pending", "issued", "cancelled", "all").contains(t)) {
            throw new IllegalArgumentException("tab chỉ nhận pending, issued, cancelled hoặc all.");
        }
        Map<String, List<MaterialRequisition>> groups = new LinkedHashMap<>();
        for (MaterialRequisition r : requisitionRepository.findByReasonStartingWithOrderByRequisitionIdAsc("KP-")) {
            Matcher m = NutritionRequisitions.MARKER.matcher(r.getReason());
            if (m.find()) {
                groups.computeIfAbsent(m.group(1) + "-" + m.group(2), k -> new ArrayList<>()).add(r);
            }
        }
        List<NutritionStockRequestDTO> out = new ArrayList<>();
        for (List<MaterialRequisition> rs : groups.values()) {
            Matcher m = NutritionRequisitions.MARKER.matcher(rs.get(0).getReason());
            m.find();
            Integer planId = Integer.parseInt(m.group(1));
            LocalDate day = LocalDate.parse(m.group(2), NutritionRequisitions.DAY);
            if (date != null && !date.equals(day)) {
                continue;
            }
            List<MaterialRequisition> live = rs.stream().filter(r -> !NutritionRequisitions.DEAD.contains(r.getStatus())).toList();
            String status = live.isEmpty() ? "CANCELLED" : live.stream().anyMatch(r -> NutritionRequisitions.PENDING.contains(r.getStatus())) ? "PENDING" : "ISSUED";
            boolean match = switch (t) {
                case "pending" -> "PENDING".equals(status);
                case "issued" -> "ISSUED".equals(status);
                case "cancelled" -> rs.stream().anyMatch(r -> NutritionRequisitions.DEAD.contains(r.getStatus()));
                default -> true;
            };
            if (!match) {
                continue;
            }
            // Tab đã hủy / thay đổi hiển thị các phiếu đã hủy; các tab khác hiển thị phiếu còn hiệu lực
            List<MaterialRequisition> shown = "cancelled".equals(t)
                    ? rs.stream().filter(r -> NutritionRequisitions.DEAD.contains(r.getStatus())).toList()
                    : (live.isEmpty() ? rs : live);
            out.add(toDto(planId, day, status, shown));
        }
        out.sort(Comparator.comparing(NutritionStockRequestDTO::getDate).thenComparing(NutritionStockRequestDTO::getPlanId));
        return out;
    }

    /** Kho cấp thức ăn một ngày của thực đơn: trừ kho từng món rồi giao việc cho ăn ngày đó cho Groom */
    @Transactional(rollbackFor = Exception.class)
    public NutritionStockRequestDTO issueDay(Integer planId, LocalDate day) {
        NutritionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực đơn #" + planId));
        List<MaterialRequisition> pending = requisitions.liveForDay(planId, day).stream()
                .filter(r -> NutritionRequisitions.PENDING.contains(r.getStatus())).toList();
        if (pending.isEmpty()) {
            throw new IllegalArgumentException("Thực đơn #" + planId + " không có phiếu cấp thức ăn chờ cấp ngày " + day.format(NutritionRequisitions.VN_DATE) + ".");
        }
        if (day.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Không cấp thức ăn cho ngày đã qua (" + day.format(NutritionRequisitions.VN_DATE) + ").");
        }
        var keeper = stockLedger.currentUser();
        for (MaterialRequisition r : pending) {
            try {
                stockLedger.issue(r.getItem().getItemId(), r.getQuantity(), null, "ISSUE", "REQUISITION", r.getRequisitionId(),
                        "Cấp thức ăn " + r.getRequisitionCode() + " - thực đơn #" + planId + " ngày " + day.format(NutritionRequisitions.VN_DATE));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(ex.getMessage() + " Chưa cấp được thức ăn ngày " + day.format(NutritionRequisitions.VN_DATE)
                        + " của thực đơn #" + planId + "; hãy nhập thêm hàng.");
            }
            r.setStatus("ISSUED");
            r.setIssuedQuantity(r.getQuantity());
            r.setIssuedTo(r.getRequestedBy());
            r.setIssuedBy(keeper);
            r.setIssuedAt(LocalDateTime.now());
            requisitionRepository.save(r);
        }
        requisitionRepository.flush();
        int tasks = feedTaskService.generateForPlan(plan, day, true);
        auditTrail.record("ISSUE_NUTRITION_STOCK", "Material_Requisitions", "status=SUBMITTED",
                "status=ISSUED | thực đơn #" + planId + " ngựa " + plan.getHorse().getHorseName() + " ngày " + day.format(NutritionRequisitions.VN_DATE)
                        + " | " + pending.size() + " món | giao " + tasks + " việc cho ăn");
        notifications.push(plan.getCreatedBy(), "NUTRITION_STOCK", "Kho đã cấp thức ăn: thực đơn #" + planId,
                "Kho đã cấp thức ăn ngày " + day.format(NutritionRequisitions.VN_DATE) + " cho ngựa " + plan.getHorse().getHorseName()
                        + ". Groom nhận " + tasks + " việc cho ăn.", null);
        return list("all", day).stream().filter(d -> d.getPlanId().equals(planId)).findFirst().orElse(null);
    }

    private NutritionStockRequestDTO toDto(Integer planId, LocalDate day, String status, List<MaterialRequisition> rs) {
        NutritionPlan plan = planRepository.findById(planId).orElse(null);
        MaterialRequisition first = rs.get(0);
        List<NutritionStockRequestDTO.Line> lines = rs.stream().map(r -> {
            InventoryItem item = r.getItem();
            entityManager.refresh(item);
            double available = (item.getCurrentStock() != null ? item.getCurrentStock() : 0) - (item.getHeldStock() != null ? item.getHeldStock() : 0);
            return NutritionStockRequestDTO.Line.builder()
                    .requisitionId(r.getRequisitionId())
                    .requisitionCode(r.getRequisitionCode())
                    .itemId(item.getItemId())
                    .itemCode(item.getItemCode())
                    .itemName(item.getItemName())
                    .unit(item.getUnit())
                    .quantity(r.getQuantity())
                    .availableStock(StockLedgerService.round2(available))
                    .issuedQuantity(r.getIssuedQuantity())
                    .status(r.getStatus())
                    .note(r.getReason())
                    .build();
        }).collect(Collectors.toList());
        boolean canIssue = "PENDING".equals(status) && !day.isBefore(LocalDate.now())
                && lines.stream().filter(l -> NutritionRequisitions.PENDING.contains(l.getStatus())).allMatch(l -> l.getAvailableStock() >= l.getQuantity());
        return NutritionStockRequestDTO.builder()
                .planId(planId)
                .planType(plan != null ? plan.getPlanType() : null)
                .planStatus(plan != null ? plan.getStatus() : null)
                .horseId(plan != null ? plan.getHorse().getHorseId() : null)
                .horseName(plan != null ? plan.getHorse().getHorseName() : null)
                .stallCode(plan != null && plan.getHorse().getStall() != null ? plan.getHorse().getStall().getStallCode() : null)
                .barnZone(first.getBarnZone())
                .date(day)
                .createdByName(first.getRequestedBy() != null ? first.getRequestedBy().getFullName() : null)
                .status(status)
                .canIssue(canIssue)
                .requestedAt(first.getRequestedAt())
                .issuedAt(rs.stream().map(MaterialRequisition::getIssuedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null))
                .issuedByName(rs.stream().map(MaterialRequisition::getIssuedBy).filter(Objects::nonNull).map(u -> u.getFullName()).findFirst().orElse(null))
                .lines(lines)
                .build();
    }
}
