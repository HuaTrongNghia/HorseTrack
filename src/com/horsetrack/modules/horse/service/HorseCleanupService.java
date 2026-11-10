package com.horsetrack.equinecare.modules.horse.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.notification.service.AppNotificationService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Xóa ngựa tạo nhầm (thay cho DATABASE_delete_one_horse.sql): chỉ ngựa chưa được cấp chuồng và chưa có dữ liệu nghiệp vụ
 * (chỉ có hồ sơ đăng ký, giấy tờ, yêu cầu phân công). Ngựa đã có giáo án, ca khám, hóa đơn... thì báo danh sách và không xóa.
 */
@Service
@RequiredArgsConstructor
public class HorseCleanupService {

    private static final String[][] USAGE = {
            {"Giáo án", "SELECT COUNT(*) FROM Training_Plans WHERE horse_id = ?1"},
            {"Ca khám", "SELECT COUNT(*) FROM Vet_Cases WHERE horse_id = ?1"},
            {"Bệnh án", "SELECT COUNT(*) FROM Medical_Records WHERE horse_id = ?1"},
            {"Phác đồ", "SELECT COUNT(*) FROM Treatment_Protocols WHERE horse_id = ?1"},
            {"Tiêm phòng", "SELECT COUNT(*) FROM Vaccinations WHERE horse_id = ?1"},
            {"Khẩu phần", "SELECT COUNT(*) FROM Nutrition_Plans WHERE horse_id = ?1"},
            {"Yêu cầu đổi khẩu phần", "SELECT COUNT(*) FROM Diet_Change_Requests WHERE horse_id = ?1"},
            {"Việc chăm sóc", "SELECT COUNT(*) FROM Daily_Tasks WHERE horse_id = ?1"},
            {"Việc chăm sóc cũ", "SELECT COUNT(*) FROM care_tasks WHERE horse_id = ?1"},
            {"Vết thương", "SELECT COUNT(*) FROM Injury_Marks WHERE horse_id = ?1"},
            {"Hóa đơn", "SELECT COUNT(*) FROM Invoices WHERE horse_id = ?1"},
            {"Đề xuất thi đấu", "SELECT COUNT(*) FROM Competition_Entries WHERE horse_id = ?1"},
    };

    private final HorseRepository horseRepository;
    private final EntityManager entityManager;
    private final AuditTrail auditTrail;
    private final AppNotificationService appNotifications;

    @Transactional
    public void deleteUnusedHorse(Integer horseId, String reason) {
        if (!StringUtils.hasText(reason) || reason.trim().length() < 5) {
            throw new IllegalArgumentException("Phải ghi lý do xóa (ít nhất 5 ký tự).");
        }
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + horseId));
        if (horse.getStall() != null) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " đã được duyệt và cấp chuồng " + horse.getStall().getStallCode()
                    + ", không xóa được. Chỉ xóa ngựa tạo nhầm chưa được duyệt.");
        }
        List<String> used = new ArrayList<>();
        for (String[] u : USAGE) {
            Number n = (Number) entityManager.createNativeQuery(u[1]).setParameter(1, horseId).getSingleResult();
            if (n.intValue() > 0) {
                used.add(u[0] + " (" + n.intValue() + ")");
            }
        }
        if (!used.isEmpty()) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " đã có dữ liệu: " + String.join(", ", used) + ". Không xóa để giữ lịch sử.");
        }
        String name = horse.getHorseName();
        var owner = horse.getOwner();
        for (String sql : List.of(
                "DELETE FROM Registration_Checks WHERE registration_id IN (SELECT registration_id FROM Horse_Registrations WHERE horse_id = ?1)",
                "DELETE FROM Registration_Attachments WHERE registration_id IN (SELECT registration_id FROM Horse_Registrations WHERE horse_id = ?1)",
                "DELETE FROM Horse_Registrations WHERE horse_id = ?1",
                "DELETE FROM Assignment_Requests WHERE horse_id = ?1")) {
            entityManager.createNativeQuery(sql).setParameter(1, horseId).executeUpdate();
        }
        entityManager.flush();
        entityManager.clear();
        entityManager.createNativeQuery("DELETE FROM Horses WHERE horse_id = ?1").setParameter(1, horseId).executeUpdate();
        auditTrail.record("DELETE_HORSE", "Horses", "horse_id=" + horseId,
                "Xóa ngựa tạo nhầm " + name + " (horse_id=" + horseId + ", chủ " + (owner != null ? owner.getUsername() : "?") + ") | Lý do: " + reason.trim());
        appNotifications.push(owner, "HORSE_REGISTRATION", "Hồ sơ ngựa " + name + " đã bị xóa",
                "Quản lý CLB đã xóa hồ sơ ngựa " + name + " (chưa được duyệt). Lý do: " + reason.trim() + ". Bạn có thể đăng ký lại nếu cần.", "/owner/registrations");
    }
}
