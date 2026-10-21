package com.horsetrack.equinecare.modules.auth.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.auth.dto.ChangePasswordRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserProfileResponseDTO;
import com.horsetrack.equinecare.modules.auth.security.OtpService;
import com.horsetrack.equinecare.modules.auth.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final OtpService otpService;

    @GetMapping("/my-profile")
    public ResponseEntity<ApiResponse<UserProfileResponseDTO>> getMyProfile() {
        UserProfileResponseDTO profile = accountService.getMyProfile();
        return ResponseEntity.ok(ApiResponse.ok("Fetched profile successfully", profile));
    }

    // Bước 1: gửi mã OTP đổi mật khẩu tới email của tài khoản đang đăng nhập
    @PostMapping("/change-password/otp")
    public ResponseEntity<ApiResponse<String>> requestChangePasswordOtp() {
        String maskedEmail = accountService.requestChangePasswordOtp();
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi mã OTP tới " + maskedEmail + ". Mã có hiệu lực " + otpService.ttlText() + ".", maskedEmail));
    }

    // Bước 2: đổi mật khẩu với mật khẩu cũ + mã OTP
    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequestDTO request) {
        accountService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Đổi mật khẩu thành công. Các thiết bị khác sẽ phải đăng nhập lại.", null));
    }
}
