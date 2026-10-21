package com.horsetrack.equinecare.modules.nutrition.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.entity.GroomProfile;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.GroomProfileRepository;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Phạm vi dữ liệu khẩu phần (GEN-06, BR_03):
 * - Xem: Trainer chỉ ngựa được phân công; Horse Owner chỉ ngựa của mình; Groom chỉ ngựa trong khu phụ trách;
 *   Bác sĩ thú y, Club Manager xem mọi ngựa.
 * - Lập / sửa / hủy: ngựa khỏe → Trainer phụ trách ngựa (khẩu phần STANDARD);
 *   ngựa đang khóa huấn luyện (bị thương) → Bác sĩ thú y (khẩu phần MEDICAL). Club Manager chỉ xem.
 */
@Component
@RequiredArgsConstructor
public class NutritionScope {

    public static final String ROLE_TRAINER = "Trainer";
    public static final String ROLE_VET = "Veterinarian";
    public static final String ROLE_GROOM = "Groom";
    public static final String ROLE_OWNER = "Horse Owner";

    private final UserRepository userRepository;
    private final GroomProfileRepository groomProfileRepository;

    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Người dùng chưa được xác thực.");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + auth.getName()));
    }

    public boolean canView(User user, Horse horse) {
        if (horse == null) {
            return false;
        }
        return switch (String.valueOf(user.getRoleName())) {
            case ROLE_TRAINER -> horse.getAssignedTrainer() != null && horse.getAssignedTrainer().getUserId().equals(user.getUserId());
            case ROLE_OWNER -> horse.getOwner() != null && horse.getOwner().getUserId().equals(user.getUserId());
            case ROLE_GROOM -> sameZone(user, horse);
            default -> true;
        };
    }

    public void checkView(User user, Horse horse) {
        if (!canView(user, horse)) {
            throw new AccessDeniedException(switch (String.valueOf(user.getRoleName())) {
                case ROLE_TRAINER -> "Bạn không được Club Manager phân công phụ trách ngựa " + horse.getHorseName() + ".";
                case ROLE_OWNER -> "Bạn không phải chủ của ngựa này.";
                case ROLE_GROOM -> "Ngựa " + horse.getHorseName() + " không thuộc khu chăm sóc của bạn.";
                default -> "Bạn không có quyền xem dữ liệu của ngựa này.";
            });
        }
    }

    /** BR_03: ai được lập khẩu phần cho ngựa ở thời điểm hiện tại */
    public void checkCanCreate(User user, Horse horse) {
        boolean locked = Boolean.TRUE.equals(horse.getIsLocked());
        String role = String.valueOf(user.getRoleName());
        if (locked && !ROLE_VET.equals(role)) {
            throw new AccessDeniedException("Ngựa " + horse.getHorseName() + " đang bị khóa huấn luyện (chấn thương). Chỉ Bác sĩ thú y được lập khẩu phần lúc này (BR_03).");
        }
        if (!locked && !ROLE_TRAINER.equals(role)) {
            throw new AccessDeniedException("Ngựa " + horse.getHorseName() + " khỏe mạnh. Khẩu phần do HLV phụ trách ngựa lập (BR_03).");
        }
        checkView(user, horse);
    }

    /** Sửa / hủy / xóa: HLV phụ trách với khẩu phần thường của ngựa khỏe; bác sĩ với khẩu phần y tế hoặc ngựa đang khóa */
    public void checkCanManage(User user, NutritionPlan plan) {
        Horse horse = plan.getHorse();
        String role = String.valueOf(user.getRoleName());
        boolean medical = "MEDICAL".equals(plan.getPlanType()) || Boolean.TRUE.equals(horse.getIsLocked());
        if (ROLE_VET.equals(role)) {
            if (!medical) {
                throw new AccessDeniedException("Đây là khẩu phần thường của ngựa khỏe; chỉ HLV phụ trách ngựa được thay đổi (BR_03).");
            }
            return;
        }
        if (ROLE_TRAINER.equals(role)) {
            if (medical) {
                throw new AccessDeniedException("Ngựa đang bị khóa huấn luyện hoặc đây là khẩu phần y tế; chỉ Bác sĩ thú y được thay đổi (BR_03).");
            }
            checkView(user, horse);
            return;
        }
        throw new AccessDeniedException("Bạn chỉ được xem khẩu phần.");
    }

    public void checkGroomZone(User user, Horse horse) {
        if (ROLE_GROOM.equals(user.getRoleName()) && !sameZone(user, horse)) {
            throw new AccessDeniedException("Ngựa " + horse.getHorseName() + " không thuộc khu chăm sóc của bạn.");
        }
    }

    private boolean sameZone(User user, Horse horse) {
        String zone = groomProfileRepository.findById(user.getUserId()).map(GroomProfile::getAssignedBarnZone).orElse(null);
        String horseZone = horse.getStall() != null ? horse.getStall().getBarnZone() : null;
        return zone != null && horseZone != null && zone.trim().equalsIgnoreCase(horseZone.trim());
    }
}
