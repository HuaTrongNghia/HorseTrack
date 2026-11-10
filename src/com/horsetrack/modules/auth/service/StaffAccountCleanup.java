package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Xóa tài khoản nhân viên tạo nhầm (thay cho DATABASE_delete_one_user.sql): chỉ tài khoản nhân viên chưa xác nhận email
 * và chưa có dữ liệu nghiệp vụ. Có dữ liệu thì báo danh sách, hãy khóa tài khoản để giữ lịch sử.
 * Audit_Logs và Email_Logs giữ lại (user_id tự chuyển NULL).
 */
@Service
@RequiredArgsConstructor
public class StaffAccountCleanup {

    private static final Set<String> NOT_STAFF = Set.of("Horse Owner", "Club Manager");

    // [nhãn, câu đếm theo :id] - bảng nghiệp vụ tham chiếu tới tài khoản
    private static final String[][] USAGE = {
            {"Ngựa đang phụ trách (HLV)", "SELECT COUNT(*) FROM Horses WHERE assigned_trainer_id = ?1"},
            {"Chuồng đang phụ trách (Mã phu)", "SELECT COUNT(*) FROM Stalls WHERE assigned_groom_id = ?1"},
            {"Buổi tập", "SELECT COUNT(*) FROM training_sessions WHERE assigned_to = ?1"},
            {"Giáo án", "SELECT COUNT(*) FROM Training_Plans WHERE head_trainer_id = ?1"},
            {"Việc chăm sóc", "SELECT COUNT(*) FROM Daily_Tasks WHERE groom_id = ?1"},
            {"Việc chăm sóc cũ", "SELECT COUNT(*) FROM care_tasks WHERE assigned_to = ?1"},
            {"Ca khám", "SELECT COUNT(*) FROM Vet_Cases WHERE reported_by = ?1 OR assigned_vet_id = ?1"},
            {"Bệnh án", "SELECT COUNT(*) FROM Medical_Records WHERE vet_id = ?1"},
            {"Phác đồ", "SELECT COUNT(*) FROM Treatment_Protocols WHERE vet_id = ?1"},
            {"Tiến trình phác đồ", "SELECT COUNT(*) FROM Protocol_Progress_Notes WHERE written_by = ?1"},
            {"Khẩu phần", "SELECT COUNT(*) FROM Nutrition_Plans WHERE created_by_id = ?1"},
            {"Yêu cầu đổi khẩu phần", "SELECT COUNT(*) FROM Diet_Change_Requests WHERE requested_by = ?1 OR decided_by = ?1"},
            {"Yêu cầu vật tư", "SELECT COUNT(*) FROM Material_Requisitions WHERE requested_by = ?1 OR issued_by = ?1 OR issued_to = ?1"},
            {"Nhật ký kho", "SELECT COUNT(*) FROM Stock_Transactions WHERE performed_by = ?1"},
            {"Kiểm kê", "SELECT COUNT(*) FROM Stock_Counts WHERE counted_by = ?1"},
            {"Giữ hàng phác đồ", "SELECT COUNT(*) FROM Stock_Holds WHERE checked_by = ?1"},
            {"Đơn mua", "SELECT COUNT(*) FROM Purchase_Orders WHERE created_by = ?1"},
            {"Đề xuất thi đấu", "SELECT COUNT(*) FROM Competition_Entries WHERE trainer_id = ?1 OR vet_id = ?1 OR proposed_by = ?1"},
            {"Yêu cầu phân công đang chờ", "SELECT COUNT(*) FROM Assignment_Requests WHERE status = 'PENDING' AND (target_user_id = ?1 OR previous_user_id = ?1)"},
    };

    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final AuditTrail auditTrail;

    /** Tài khoản xóa được theo vai trò và trạng thái xác nhận (chưa xét dữ liệu nghiệp vụ) */
    public static boolean deletableKind(User u) {
        return u != null && !NOT_STAFF.contains(u.getRoleName()) && u.getEmailVerifiedAt() == null;
    }

    @Transactional
    public void deleteUnconfirmedStaff(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + userId));
        String me = SecurityContextHolder.getContext().getAuthentication().getName();
        if (user.getUsername().equals(me)) {
            throw new IllegalArgumentException("Không xóa được tài khoản đang đăng nhập.");
        }
        if (NOT_STAFF.contains(user.getRoleName())) {
            throw new IllegalArgumentException("Chỉ xóa được tài khoản nhân viên tạo nhầm, không xóa chủ ngựa hoặc Quản lý CLB.");
        }
        if (user.getEmailVerifiedAt() != null) {
            throw new IllegalArgumentException("Nhân viên " + user.getFullName() + " đã xác nhận tài khoản. Hãy khóa tài khoản (PUT /users/{id}/lock) để giữ lịch sử.");
        }
        List<String> used = new ArrayList<>();
        for (String[] u : USAGE) {
            Number n = (Number) entityManager.createNativeQuery(u[1]).setParameter(1, userId).getSingleResult();
            if (n.intValue() > 0) {
                used.add(u[0] + " (" + n.intValue() + ")");
            }
        }
        if (!used.isEmpty()) {
            throw new IllegalArgumentException("Tài khoản " + user.getUsername() + " đã có dữ liệu: " + String.join(", ", used)
                    + ". Không xóa để giữ lịch sử; hãy khóa tài khoản.");
        }
        for (String sql : List.of(
                "DELETE FROM Refresh_Tokens WHERE user_id = ?1",
                "DELETE FROM Trainer_Profiles WHERE user_id = ?1",
                "DELETE FROM Groom_Profiles WHERE user_id = ?1",
                "DELETE FROM Veterinarian_Profiles WHERE user_id = ?1",
                "DELETE FROM Club_Manager_Profiles WHERE user_id = ?1",
                "DELETE FROM Horse_Owner_Profiles WHERE user_id = ?1",
                "DELETE FROM App_Notifications WHERE user_id = ?1",
                "DELETE FROM Assignment_Requests WHERE target_user_id = ?1 OR previous_user_id = ?1",
                "UPDATE Horse_Registrations SET decided_by = NULL WHERE decided_by = ?1",
                "UPDATE Registration_Checks SET checked_by = NULL WHERE checked_by = ?1",
                "UPDATE Payment_Transactions SET verified_by = NULL WHERE verified_by = ?1",
                "UPDATE Injury_Marks SET created_by = NULL WHERE created_by = ?1",
                "UPDATE Vaccinations SET done_by = NULL WHERE done_by = ?1",
                "UPDATE Horses SET locked_by = NULL WHERE locked_by = ?1")) {
            entityManager.createNativeQuery(sql).setParameter(1, userId).executeUpdate();
        }
        entityManager.flush();
        entityManager.clear();
        entityManager.createNativeQuery("DELETE FROM Users WHERE user_id = ?1").setParameter(1, userId).executeUpdate();
        auditTrail.record("DELETE_STAFF_ACCOUNT", "Users", "user_id=" + userId,
                "Xóa tài khoản nhân viên chưa xác nhận " + user.getUsername() + " <" + user.getEmail() + "> (vai trò " + user.getRoleName() + ")");
    }
}
