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

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final EmailService emailService;

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
        }

        return dto;
    }

    @Override
    public String requestChangePasswordOtp() {
        User user = currentUser();
        if (!StringUtils.hasText(user.getEmail())) {
            throw new IllegalArgumentException("Tài khoản chưa có email để nhận mã OTP.");
        }
        otpService.checkAndMarkCooldown(user.getEmail(), OtpService.Purpose.CHANGE_PASSWORD);
        String otp = otpService.issue(user.getEmail(), OtpService.Purpose.CHANGE_PASSWORD);
        try {
            emailService.sendOtp(user.getEmail(), user.getFullName(), otp, OtpService.Purpose.CHANGE_PASSWORD.getLabel());
        } catch (EmailSendException ex) {
            otpService.invalidate(user.getEmail(), OtpService.Purpose.CHANGE_PASSWORD);
            throw ex;
        }
        return maskEmail(user.getEmail());
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

    // Che bớt email trong phản hồi: nguyenvana@gmail.com -> ng*******@gmail.com
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) {
            return email;
        }
        return email.substring(0, 2) + "*".repeat(at - 2) + email.substring(at);
    }
}
