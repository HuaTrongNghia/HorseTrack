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

    // Sửa họ tên, số điện thoại của chính mình
    @PutMapping("/my-profile")
    public ResponseEntity<ApiResponse<UserProfileResponseDTO>> updateMyProfile(
            @Valid @RequestBody com.horsetrack.equinecare.modules.auth.dto.ProfileUpdateRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật thông tin cá nhân", accountService.updateMyProfile(request)));
    }

    // Ảnh đại diện: multipart field "file", PNG / JPEG / WEBP, tối đa 1 MB
    @PostMapping(value = "/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserProfileResponseDTO>> updateAvatar(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật ảnh đại diện", accountService.updateAvatar(file)));
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
