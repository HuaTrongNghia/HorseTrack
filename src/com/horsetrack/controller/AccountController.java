package com.horsetrack.controller;

import com.horsetrack.dto.*;
import com.horsetrack.response.ApiResponse;
import com.horsetrack.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "*") // Bắt buộc để Frontend có thể gọi API
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/register-owner")
    public ResponseEntity<ApiResponse<String>> registerOwner(@RequestBody OwnerRegistrationDTO dto) {
        try {
            if (!dto.isAgreeToTerms()) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Vui lòng đồng ý điều khoản.", null));
            }
            if (dto.getPassword() == null || !dto.getPassword().equals(dto.getConfirmPassword())) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Mật khẩu xác nhận không khớp.", null));
            }
            if (dto.getPassword().length() < 8) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Mật khẩu tối thiểu 8 ký tự.", null));
            }

            accountService.createOwnerAccount(dto);
            return ResponseEntity.ok(new ApiResponse<>(200, "Tạo tài khoản Chủ ngựa thành công!", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestBody ForgotPasswordDTO dto) {
        try {
            accountService.generateAndSendOtp(dto);
            return ResponseEntity.ok(new ApiResponse<>(200, "Mã OTP đã được gửi đến email của bạn.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@RequestBody ResetPasswordDTO dto) {
        try {
            if (dto.getNewPassword() == null || dto.getNewPassword().length() < 8) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Mật khẩu mới tối thiểu 8 ký tự.", null));
            }
            accountService.resetPassword(dto);
            return ResponseEntity.ok(new ApiResponse<>(200, "Đổi mật khẩu thành công! Vui lòng đăng nhập lại.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(@RequestBody ChangePasswordDTO dto) {
        try {
            if (dto.getNewPassword() == null || dto.getNewPassword().length() < 8) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Mật khẩu mới tối thiểu 8 ký tự.", null));
            }
            accountService.changePassword(dto);
            return ResponseEntity.ok(new ApiResponse<>(200, "Cập nhật mật khẩu mới thành công.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }
}