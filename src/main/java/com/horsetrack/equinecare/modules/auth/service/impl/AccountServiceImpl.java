package com.horsetrack.equinecare.modules.auth.service.impl;

import com.horsetrack.equinecare.modules.auth.dto.ChangePasswordRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserProfileResponseDTO;
import com.horsetrack.equinecare.modules.auth.entity.TrainerProfile;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.entity.VeterinarianProfile;
import com.horsetrack.equinecare.modules.auth.repository.RefreshTokenRepository;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.auth.security.OtpService;
import com.horsetrack.equinecare.modules.auth.service.AccountService;
import com.horsetrack.equinecare.modules.notification.exception.EmailSendException;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final EmailService emailService;
    private final com.horsetrack.equinecare.modules.audit.service.AuditTrail auditTrail;

    private static final long AVATAR_MAX_BYTES = 1024 * 1024;
    private static final java.util.Set<String> AVATAR_TYPES = java.util.Set.of("image/png", "image/jpeg", "image/webp");

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponseDTO getMyProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfileResponseDTO dto = UserProfileResponseDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .roleName(user.getRoleName())
                .isActive(user.getIsActive())
                .avatarUrl(userRepository.findAvatarUrl(user.getUserId()))
                .build();

        if ("Trainer".equalsIgnoreCase(user.getRoleName())) {
            TrainerProfile trainer = user.getTrainerProfile();
            if (trainer != null) {
                dto.setLicenseNumber(trainer.getLicenseNumber());
                dto.setExperienceYears(trainer.getExperienceYears());
            }
        } else if ("VETERINARIAN".equalsIgnoreCase(user.getRoleName())) {
            VeterinarianProfile vet = user.getVeterinarianProfile();
            if (vet != null) {
                dto.setLicenseNumber(vet.getVetLicenseNo());
                dto.setClinicAffiliation(vet.getClinicAffiliation());
            }
        } else if ("Groom".equalsIgnoreCase(user.getRoleName())) {
            com.horsetrack.equinecare.modules.auth.entity.GroomProfile groom = user.getGroomProfile();
            if (groom != null) {
                dto.setSpecialization(groom.getAssignedBarnZone());
            }
        }

        return dto;
    }

    @Override
    @Transactional
    public UserProfileResponseDTO updateMyProfile(com.horsetrack.equinecare.modules.auth.dto.ProfileUpdateRequestDTO request) {
        User user = currentUser();
        String fullName = request.getFullName().trim();
        String phone = com.horsetrack.equinecare.modules.auth.service.AuthServiceImpl.normalizePhone(request.getPhone());
        if (phone != null && !phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("Số điện thoại " + phone + " đã được tài khoản khác sử dụng.");
        }
        String before = "full_name=" + user.getFullName() + " | phone=" + user.getPhone();
        user.setFullName(fullName);
        user.setPhone(phone);
        userRepository.save(user);
        auditTrail.record("UPDATE_PROFILE", "Users", before, "full_name=" + fullName + " | phone=" + phone + " | " + user.getUsername());
        return getMyProfile();
    }

    @Override
    @Transactional
    public UserProfileResponseDTO updateAvatar(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Chưa chọn ảnh đại diện.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!AVATAR_TYPES.contains(type)) {
            throw new IllegalArgumentException("Ảnh đại diện phải là PNG, JPEG hoặc WEBP.");
        }
        if (file.getSize() > AVATAR_MAX_BYTES) {
            throw new IllegalArgumentException("Ảnh đại diện tối đa 1 MB.");
        }
        User user = currentUser();
        try {
            userRepository.updateAvatarUrl(user.getUserId(), "data:" + type + ";base64," + java.util.Base64.getEncoder().encodeToString(file.getBytes()));
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Không đọc được ảnh tải lên.");
        }
        auditTrail.record("UPDATE_AVATAR", "Users", null, "avatar " + type + " " + file.getSize() + " bytes | " + user.getUsername());
        return getMyProfile();
    }

    @Override
    public String requestChangePasswordOtp() {
        User user = currentUser();
        if (!StringUtils.hasText(user.getEmail())) {
            throw new IllegalArgumentException("Tài khoản chưa có email để nhận mã OTP.");
        }
        otpService.checkAndMarkCooldown(user.getEmail(), OtpService.Purpose.CHANGE_PASSWORD);
        String email = user.getEmail();
        String otp = otpService.issue(email, OtpService.Purpose.CHANGE_PASSWORD);
        // Gửi ở nền; gửi lỗi (kể cả sau khi đã trả lời) thì hủy mã để người dùng gửi lại ngay
        CompletableFuture<Void> sending = emailService.sendOtp(email, user.getFullName(), otp, OtpService.Purpose.CHANGE_PASSWORD.getLabel())
                .whenComplete((ok, ex) -> {
                    if (ex != null) {
                        otpService.invalidate(email, OtpService.Purpose.CHANGE_PASSWORD);
                    }
                });
        // Người dùng đang chờ mã: đợi tối đa app.mail.wait-seconds để báo lỗi gửi nếu có
        if (emailService.await(sending) == EmailService.Delivery.FAILED) {
            throw new EmailSendException("Không gửi được mã OTP tới " + maskEmail(email) + ". Vui lòng thử lại sau.", null);
        }
        return maskEmail(email);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequestDTO request) {
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới và xác nhận mật khẩu không khớp.");
        }

        User user = currentUser();
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu cũ không đúng.");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu cũ.");
        }
        // Kiểm tra OTP sau cùng để mã không bị tiêu hao khi các thông tin khác còn sai
        otpService.verify(user.getEmail(), OtpService.Purpose.CHANGE_PASSWORD, request.getOtp());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        // Đăng xuất các thiết bị khác: refresh token cũ không dùng được nữa
        refreshTokenRepository.revokeAllByUserId(user.getUserId());
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private static String maskEmail(String email) {
        return OtpService.maskEmail(email);
    }
}
