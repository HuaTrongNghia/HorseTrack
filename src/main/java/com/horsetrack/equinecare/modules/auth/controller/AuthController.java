package com.horsetrack.equinecare.modules.auth.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.auth.dto.OwnerRegisterRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.StaffCreateRequestDTO;
import com.horsetrack.equinecare.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
@Tag(name = "Authentication & Onboarding", description = "Các API xác thực và tiếp nhận tài khoản")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register-owner")
    @Operation(summary = "Khách hàng tự đăng ký", description = "Dành cho Horse Owner tự tạo tài khoản.")
    public ResponseEntity<ApiResponse<Void>> registerOwner(@Valid @RequestBody OwnerRegisterRequestDTO request) {
        try {
            authService.registerOwner(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok("Tạo tài khoản chủ ngựa thành công!", null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Không thể hoàn tất đăng ký: " + ex.getMessage()));
        }
    }

    @PostMapping("/staff/create")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Quản lý tạo nhân viên", description = "Chỉ dành cho Club Manager tạo tài khoản Staff.")
    public ResponseEntity<ApiResponse<Void>> createStaff(@Valid @RequestBody StaffCreateRequestDTO request) {
        try {
            authService.createStaff(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok("Tạo tài khoản nhân viên thành công!", null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Không thể hoàn tất tạo tài khoản: " + ex.getMessage()));
        }
    }
}