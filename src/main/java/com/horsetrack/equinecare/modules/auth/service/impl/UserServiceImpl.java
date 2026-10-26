package com.horsetrack.equinecare.modules.auth.service.impl;

import com.horsetrack.equinecare.modules.audit.repository.AuditLogRepository;
import com.horsetrack.equinecare.modules.audit.service.AuditLogService;
import com.horsetrack.equinecare.modules.auth.dto.UserLockRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserPageResponseDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserResponseDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserRoleChangeRequestDTO;
import com.horsetrack.equinecare.modules.auth.entity.*;
import com.horsetrack.equinecare.modules.auth.repository.*;
import com.horsetrack.equinecare.modules.auth.service.UserService;
import com.horsetrack.equinecare.modules.care.repository.CareTaskRepository;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final HorseRepository horseRepository;
    private final CareTaskRepository careTaskRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;
    private final com.horsetrack.equinecare.modules.notification.service.EmailService emailService;

    private final TrainerProfileRepository trainerProfileRepository;
    private final VeterinarianProfileRepository veterinarianProfileRepository;
    private final GroomProfileRepository groomProfileRepository;
    private final HorseOwnerProfileRepository horseOwnerProfileRepository;
    private final ClubManagerProfileRepository clubManagerProfileRepository;

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(auth.getName())
                .map(User::getUserId)
                .orElse(null);
    }

    private String normalizeRoleName(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if ("ClubManager".equalsIgnoreCase(trimmed) || "Club Manager".equalsIgnoreCase(trimmed)) {
            return "Club Manager";
        }
        if ("HeadTrainer".equalsIgnoreCase(trimmed) || "Trainer".equalsIgnoreCase(trimmed) || "Head Trainer".equalsIgnoreCase(trimmed)) {
            return "Trainer";
        }
        if ("Veterinarian".equalsIgnoreCase(trimmed) || "Vet".equalsIgnoreCase(trimmed)) {
            return "Veterinarian";
        }
        if ("Groom".equalsIgnoreCase(trimmed)) {
            return "Groom";
        }
        if ("WarehouseManager".equalsIgnoreCase(trimmed) || "Warehouse Manager".equalsIgnoreCase(trimmed)) {
            return "Warehouse Manager";
        }
        if ("HorseOwner".equalsIgnoreCase(trimmed) || "Horse Owner".equalsIgnoreCase(trimmed) || "Owner".equalsIgnoreCase(trimmed)) {
            return "Horse Owner";
        }
        return trimmed;
    }

    private String toFeRole(String roleName) {
        if (roleName == null) return "ClubManager";
        switch (roleName) {
            case "Club Manager": return "ClubManager";
            case "Trainer": return "HeadTrainer";
            case "Veterinarian": return "Veterinarian";
            case "Groom": return "Groom";
            case "Warehouse Manager": return "WarehouseManager";
            case "Horse Owner": return "HorseOwner";
            default: return roleName.replace(" ", "");
        }
    }

    private UserResponseDTO mapToResponseDTO(User user) {
        String status = Boolean.TRUE.equals(user.getIsActive()) ? "ACTIVE" : "LOCKED";

        // Tìm lý do khóa gần nhất nếu tài khoản bị khóa
        String lockReason = null;
        if ("LOCKED".equals(status)) {
            lockReason = auditLogRepository
                    .findFirstByTargetTableAndActionTypeAndNewValueContainingOrderByTimestampDesc(
                            "Users", "LOCK_ACCOUNT", "user_id=" + user.getUserId())
                    .map(log -> {
                        String val = log.getNewValue();
                        int idx = val.indexOf("Lý do: ");
                        return idx >= 0 ? val.substring(idx + 7) : val;
                    })
                    .orElse(null);
        }

        // Đếm số công việc chăm sóc đang mở nếu là Groom
        Integer openTasks = null;
        if ("Groom".equals(user.getRoleName())) {
            openTasks = (int) careTaskRepository.countByGroom_UserIdAndStatusNot(user.getUserId(), "DONE");
        }

        // Đếm số ngựa đang sở hữu nếu là Horse Owner
        Integer horsesOwned = null;
        if ("Horse Owner".equals(user.getRoleName())) {
            horsesOwned = (int) horseRepository.countByOwnerUserId(user.getUserId());
        }

        // Lấy thông tin chuyên môn từ các bảng Profile
        String specialization = null;
        String licenseNumber = null;
        Integer experienceYears = null;

        if (user.getGroomProfile() != null) {
            specialization = user.getGroomProfile().getAssignedBarnZone();
        } else if (user.getVeterinarianProfile() != null) {
            licenseNumber = user.getVeterinarianProfile().getVetLicenseNo();
            specialization = user.getVeterinarianProfile().getClinicAffiliation();
        } else if (user.getTrainerProfile() != null) {
            licenseNumber = user.getTrainerProfile().getLicenseNumber();
            experienceYears = user.getTrainerProfile().getExperienceYears();
        }

        return UserResponseDTO.builder()
                .userId(user.getUserId())
                .id(String.valueOf(user.getUserId()))
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .roleName(user.getRoleName())
                .role(toFeRole(user.getRoleName()))
                .isActive(user.getIsActive())
                .status(status)
                .lockReason(lockReason)
                .openTasks(openTasks != null ? openTasks : 0)
                .horsesOwned(horsesOwned != null ? horsesOwned : 0)
                .specialization(specialization)
                .licenseNumber(licenseNumber)
                .experienceYears(experienceYears)
                .lastLogin("—")
                .createdAt(user.getCreatedAt())
                .emailConfirmed(user.getEmailVerifiedAt() != null)
                .emailVerifiedAt(user.getEmailVerifiedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserPageResponseDTO getUsers(int page, int size, String search, String role, String status) {
        Pageable pageable = PageRequest.of(page, size);

        String searchPattern = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String normalizedRole = (role != null && !role.trim().isEmpty()) ? normalizeRoleName(role) : null;
        Boolean isActive = null;
        if ("ACTIVE".equalsIgnoreCase(status)) {
            isActive = true;
        } else if ("LOCKED".equalsIgnoreCase(status)) {
            isActive = false;
        }

        Page<User> userPage = userRepository.searchUsers(searchPattern, normalizedRole, isActive, pageable);

        List<UserResponseDTO> dtos = userPage.getContent().stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());

        return UserPageResponseDTO.builder()
                .users(dtos)
                .currentPage(userPage.getNumber())
                .totalPages(userPage.getTotalPages())
                .totalElements(userPage.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAllByOrderByUserIdDesc().stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("ResourceNotFoundException: Không tìm thấy người dùng với ID " + userId));
        return mapToResponseDTO(user);
    }

    @Override
    @Transactional
    public UserResponseDTO lockUser(Integer userId, UserLockRequestDTO request, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("ResourceNotFoundException: Không tìm thấy người dùng"));

        Integer currentUserId = getCurrentUserId();
        if (user.getUserId().equals(currentUserId)) {
            throw new RuntimeException("BusinessException: Bạn không thể tự khóa tài khoản của chính mình");
        }

        if ("Club Manager".equals(user.getRoleName())) {
            throw new RuntimeException("BusinessException: Không thể khóa tài khoản Quản lý CLB");
        }

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new RuntimeException("BusinessException: Tài khoản này đã bị khóa trước đó");
        }

        user.setIsActive(false);
        userRepository.save(user);

        // Buộc đăng xuất: thu hồi toàn bộ refresh token
        refreshTokenRepository.revokeAllByUserId(user.getUserId());

        // Ghi Audit Log (BR_10)
        auditLogService.log(
                currentUserId,
                "LOCK_ACCOUNT",
                "Users",
                "ACTIVE",
                "LOCKED (user_id=" + user.getUserId() + ") | Lý do: " + request.getReason(),
                ipAddress
        );

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            try {
                emailService.sendAsync("ACCOUNT_LOCKED", user.getEmail(), "[HorseTrack] Tài khoản của bạn đã bị khóa",
                        "<p>Xin chào <b>" + HtmlUtils.htmlEscape(user.getFullName()) + "</b>,</p>"
                                + "<p>Quản lý CLB đã khóa tài khoản <b>" + HtmlUtils.htmlEscape(user.getUsername()) + "</b>. "
                                + "Bạn đã bị đăng xuất và không đăng nhập được cho đến khi tài khoản được mở.</p>"
                                + "<p>Lý do: " + HtmlUtils.htmlEscape(request.getReason()) + "</p>");
            } catch (Exception ignored) {
                // Khóa tài khoản vẫn thành công nếu email lỗi
            }
        }

        return mapToResponseDTO(user);
    }

    @Override
    @Transactional
    public UserResponseDTO unlockUser(Integer userId, UserLockRequestDTO request, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("ResourceNotFoundException: Không tìm thấy người dùng"));

        Integer currentUserId = getCurrentUserId();

        if (Boolean.TRUE.equals(user.getIsActive())) {
            throw new RuntimeException("BusinessException: Tài khoản này đang hoạt động bình thường");
        }

        user.setIsActive(true);
        userRepository.save(user);

        // Ghi Audit Log (BR_10)
        auditLogService.log(
                currentUserId,
                "UNLOCK_ACCOUNT",
                "Users",
                "LOCKED",
                "ACTIVE (user_id=" + user.getUserId() + ") | Lý do: " + request.getReason(),
                ipAddress
        );

        return mapToResponseDTO(user);
    }

    @Override
    @Transactional
    public UserResponseDTO changeRole(Integer userId, UserRoleChangeRequestDTO request, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("ResourceNotFoundException: Không tìm thấy người dùng"));

        Integer currentUserId = getCurrentUserId();
        if (user.getUserId().equals(currentUserId)) {
            throw new RuntimeException("BusinessException: Không thể tự đổi vai trò của chính mình qua tính năng này");
        }

        String currentRole = user.getRoleName();
        String newRole = normalizeRoleName(request.getRoleName());

        if (currentRole.equals(newRole)) {
            throw new RuntimeException("BusinessException: Người dùng hiện tại đã có vai trò này (" + currentRole + ")");
        }

        // BR_06: Kiểm tra xung đột lợi ích
        if ("Horse Owner".equals(currentRole) && !"Horse Owner".equals(newRole)) {
            long horseCount = horseRepository.countByOwnerUserId(user.getUserId());
            if (horseCount > 0) {
                throw new RuntimeException("BusinessException: " + user.getFullName() + " đang sở hữu " + horseCount
                        + " ngựa trong CLB - không thể nhận vai trò nhân sự (xung đột lợi ích BR_06).");
            }
        }

        if (!"Horse Owner".equals(currentRole) && "Horse Owner".equals(newRole)) {
            throw new RuntimeException("BusinessException: Nhân sự đang công tác tại CLB không thể chuyển thành Chủ ngựa (xung đột lợi ích BR_06).");
        }

        user.setRoleName(newRole);

        // Tạo profile mới tương ứng nếu chưa có
        switch (newRole) {
            case "Trainer":
                if (user.getTrainerProfile() == null && !trainerProfileRepository.existsById(user.getUserId())) {
                    trainerProfileRepository.save(TrainerProfile.builder()
                            .user(user)
                            .licenseNumber("N/A")
                            .experienceYears(0)
                            .build());
                }
                break;
            case "Veterinarian":
                if (user.getVeterinarianProfile() == null && !veterinarianProfileRepository.existsById(user.getUserId())) {
                    veterinarianProfileRepository.save(VeterinarianProfile.builder()
                            .user(user)
                            .vetLicenseNo("N/A")
                            .clinicAffiliation("Phòng khám thú y CLB")
                            .build());
                }
                break;
            case "Groom":
                if (user.getGroomProfile() == null && !groomProfileRepository.existsById(user.getUserId())) {
                    groomProfileRepository.save(GroomProfile.builder()
                            .user(user)
                            .assignedBarnZone("Khu A")
                            .build());
                }
                break;
            case "Club Manager":
                if (user.getClubManagerProfile() == null && !clubManagerProfileRepository.existsById(user.getUserId())) {
                    clubManagerProfileRepository.save(ClubManagerProfile.builder()
                            .user(user)
                            .department("Ban Quản Lý")
                            .approvalLimit(BigDecimal.valueOf(100000000))
                            .build());
                }
                break;
            case "Horse Owner":
                if (user.getHorseOwnerProfile() == null && !horseOwnerProfileRepository.existsById(user.getUserId())) {
                    horseOwnerProfileRepository.save(HorseOwnerProfile.builder()
                            .user(user)
                            .billingAddress("")
                            .build());
                }
                break;
            case "Warehouse Manager":
                // Nhân viên kho chỉ dùng bảng Users
                break;
            default:
                throw new RuntimeException("BusinessException: Vai trò không hợp lệ: " + newRole);
        }

        User savedUser = userRepository.save(user);

        // Buộc đăng nhập lại để nhận quyền và token mới
        refreshTokenRepository.revokeAllByUserId(user.getUserId());

        // Ghi Audit Log (BR_10)
        auditLogService.log(
                currentUserId,
                "CHANGE_ROLE",
                "Users",
                currentRole,
                newRole + " (user_id=" + user.getUserId() + ") | Lý do: " + request.getReason(),
                ipAddress
        );

        return mapToResponseDTO(savedUser);
    }
}
