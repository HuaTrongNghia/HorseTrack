package com.horsetrack.equinecare.modules.auth.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.auth.dto.RegisterRequestDTO;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*") // Cho phép Frontend (React/Vue/HTML) gọi API từ các port khác nhau mà không bị lỗi CORS
@Tag(name = "Authentication & Onboarding", description = "Các API xác thực và tiếp nhận tài khoản")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Endpoint tiếp nhận thông tin từ form đăng ký (Create Your Account).
     * Method: POST
     * URL: <a href="http://localhost:8080/api/v1/auth/register">http://localhost:8080/api/v1/auth/register</a>
     */
    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản thành viên mới", description = "Tiếp nhận Full Name, Email, Phone và Role để tạo tài khoản mới.")
    public ResponseEntity<ApiResponse<User>> registerAccount(@Valid @RequestBody RegisterRequestDTO request) {
        try {
            User createdUser = authService.register(request);

            // Xóa hash mật khẩu trước khi trả về Frontend nhằm bảo mật tuyệt đối
            createdUser.setPasswordHash(null);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok("Tạo tài khoản thành công!", createdUser));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Không thể hoàn tất đăng ký: " + ex.getMessage()));
        }
    }
}