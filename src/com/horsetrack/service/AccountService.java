package com.horsetrack.service;

import com.horsetrack.dto.*;
import com.horsetrack.entity.HorseOwnerProfile;
import com.horsetrack.entity.User;
import com.horsetrack.repository.HorseOwnerProfileRepository;
import com.horsetrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final HorseOwnerProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    // Bộ nhớ RAM lưu tạm OTP (Thực tế dự án lớn sẽ dùng Redis)
    private final Map<String, String> otpStorage = new HashMap<>();

    // --- 1. ĐĂNG KÝ TÀI KHOẢN CHỦ NGỰA ---
    @Transactional
    public void createOwnerAccount(OwnerRegistrationDTO dto) throws Exception {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new Exception("Email này đã được sử dụng. Vui lòng đăng nhập.");
        }
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new Exception("Tên đăng nhập này đã tồn tại.");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone() != null ? dto.getPhone() : "");
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setRoleName("Horse Owner");
        user.setActive(true);

        User savedUser = userRepository.save(user);

        HorseOwnerProfile profile = new HorseOwnerProfile();
        profile.setUser(savedUser);

        // Chặn lỗi not-null của Hibernate
        profile.setBillingAddress(dto.getBillingAddress() != null && !dto.getBillingAddress().trim().isEmpty()
                ? dto.getBillingAddress() : "Chưa cập nhật");
        profile.setTaxCode(dto.getTaxCode() != null && !dto.getTaxCode().trim().isEmpty()
                ? dto.getTaxCode() : "Chưa cập nhật");

        profileRepository.save(profile);
    }

    // --- 2. QUÊN MẬT KHẨU (BƯỚC 1: GỬI EMAIL CHỜ NHẬN OTP) ---
    public void generateAndSendOtp(ForgotPasswordDTO dto) throws Exception {
        if (!userRepository.existsByEmail(dto.getEmail())) {
            throw new Exception("Không tìm thấy tài khoản nào liên kết với email này!");
        }

        String otp = String.format("%06d", new Random().nextInt(999999));
        otpStorage.put(dto.getEmail(), otp);

        log.info("=========================================");
        log.info(">>> MÃ OTP KHÔI PHỤC CỦA {} LÀ: {}", dto.getEmail(), otp);
        log.info("=========================================");
    }

    // --- 3. QUÊN MẬT KHẨU (BƯỚC 2: NHẬP OTP VÀ ĐỔI PASSWORD) ---
    @Transactional
    public void resetPassword(ResetPasswordDTO dto) throws Exception {
        String savedOtp = otpStorage.get(dto.getEmail());

        if (savedOtp == null || !savedOtp.equals(dto.getOtp())) {
            throw new Exception("Mã OTP không hợp lệ hoặc đã hết hạn!");
        }

        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new Exception("Không tìm thấy người dùng!"));

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);

        otpStorage.remove(dto.getEmail()); // Xóa OTP sau khi dùng
    }

    // --- 4. ĐỔI MẬT KHẨU (KHI ĐÃ ĐĂNG NHẬP) ---
    @Transactional
    public void changePassword(ChangePasswordDTO dto) throws Exception {
        User user = userRepository.findByUsername(dto.getUsername())
                .orElseThrow(() -> new Exception("Không tìm thấy người dùng!"));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new Exception("Mật khẩu hiện tại không chính xác!");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }
}