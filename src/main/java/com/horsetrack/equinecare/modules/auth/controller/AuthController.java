package com.horsetrack.equinecare.modules.auth.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.auth.dto.LoginDTO;
import com.horsetrack.equinecare.modules.auth.dto.LoginResponseDTO;
import com.horsetrack.equinecare.modules.auth.dto.OwnerRegisterRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.RefreshTokenRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.StaffCreateRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.ForgotPasswordRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.ResetPasswordRequestDTO;
import com.horsetrack.equinecare.modules.auth.security.OtpService;
import com.horsetrack.equinecare.modules.auth.service.AuthService;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
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
    private final EmailService emailService;
    private final OtpService otpService;

    public AuthController(AuthService authService, EmailService emailService, OtpService otpService) {
        this.authService = authService;
        this.emailService = emailService;
        this.otpService = otpService;
    }

    @PostMapping("/register-owner")
    @Operation(summary = "Khách hàng tự đăng ký (2 bước, xác thực email bằng OTP)",
            description = "Bước 1: gửi thông tin, bỏ trống otp -> 200, mã OTP gửi tới email. Bước 2: gửi lại cùng thông tin kèm otp -> 201, tạo tài khoản.")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> registerOwner(@Valid @RequestBody OwnerRegisterRequestDTO request) {
        try {
            if (request.getOtp() == null || request.getOtp().isBlank()) {
                String masked = authService.requestOwnerRegistrationOtp(request);
                return ResponseEntity.ok(ApiResponse.ok("Đã gửi mã OTP tới " + masked + ". Nhập mã để hoàn tất đăng ký (hiệu lực " + otpService.ttlText() + ").",
                        java.util.Map.of("otpRequired", true, "email", masked, "ttlSeconds", otpService.getTtlSeconds())));
            }
            authService.registerOwner(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok("Đăng ký tài khoản chủ ngựa thành công!", java.util.Map.of("otpRequired", false)));
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
    @Operation(summary = "Quản lý tạo nhân viên", description = "Chỉ dành cho Club Manager tạo tài khoản Staff. Tạo xong hệ thống gửi email tên đăng nhập và mật khẩu cho nhân viên.")
    public ResponseEntity<ApiResponse<Void>> createStaff(@Valid @RequestBody StaffCreateRequestDTO request) {
        try {
            // Lưu tài khoản trước (transaction riêng); email chỉ gửi khi đã lưu thành công
            authService.createStaff(request);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Không thể hoàn tất tạo tài khoản: " + ex.getMessage()));
        }

        // Gửi email thất bại không hủy tài khoản đã tạo; báo để Club Manager gửi thông tin cho nhân viên theo cách khác
        // Email gửi ở nền; chờ tối đa app.mail.wait-seconds để báo kết quả, quá hạn thì vẫn tiếp tục gửi
        String email = request.getEmail().trim();
        EmailService.Delivery delivery = emailService.await(emailService.sendStaffAccount(email, request.getFullName().trim(),
                request.getRoleName(), request.getUsername().trim(), request.getPassword()));
        String message = switch (delivery) {
            case SENT -> emailService.isConfigured()
                    ? "Tạo tài khoản nhân viên thành công! Đã gửi tên đăng nhập và mật khẩu tới " + email + "."
                    : "Tạo tài khoản nhân viên thành công! (Chế độ dev: email chưa gửi thật, xem nội dung trong console server.)";
            case PENDING -> "Tạo tài khoản nhân viên thành công! Email tới " + email
                    + " đang được gửi; nếu nhân viên không nhận được sau vài phút, hãy gửi thông tin đăng nhập theo cách khác.";
            case FAILED -> "Tạo tài khoản nhân viên thành công, nhưng KHÔNG gửi được email tới " + email
                    + ". Hãy gửi thông tin đăng nhập cho nhân viên theo cách khác.";
        };
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(message, null));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Quên mật khẩu: gửi OTP", description = "Gửi mã OTP 6 số (hiệu lực 5 phút, dùng 1 lần) tới email. Luôn trả cùng một thông báo dù email có tồn tại hay không.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "Nếu email đã đăng ký, mã OTP đã được gửi tới email đó. Mã có hiệu lực " + otpService.ttlText() + ".", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Quên mật khẩu: đặt mật khẩu mới bằng OTP", description = "Đặt lại thành công thì mọi thiết bị phải đăng nhập lại.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Đặt lại mật khẩu thành công. Hãy đăng nhập bằng mật khẩu mới.", null));
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập hệ thống", description = "Đăng nhập bằng email hoặc số điện thoại (hoặc tên đăng nhập) và mật khẩu; nhận JWT Token để gọi các API khác. Trường identifier cũng nhận khóa cũ username.")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(@Valid @RequestBody LoginDTO loginRequest) {

        // Gọi hàm login vừa tạo trong AuthService
        LoginResponseDTO loginResponse = authService.login(loginRequest);

        // Sử dụng Builder pattern chuẩn
        ApiResponse<LoginResponseDTO> response = ApiResponse.<LoginResponseDTO>builder()
                .success(true)
                .message("Đăng nhập thành công")
                .data(loginResponse)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới token", description = "Dùng refresh token để lấy access token mới. Refresh token cũ bị thu hồi, hãy lưu refresh token mới được trả về.")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> refreshToken(@Valid @RequestBody RefreshTokenRequestDTO request) {
        LoginResponseDTO loginResponse = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.ok("Làm mới token thành công", loginResponse));
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất", description = "Thu hồi refresh token và chặn access token hiện tại (nếu có gửi kèm header Authorization).")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequestDTO request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        String accessToken = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : null;
        authService.logout(request, accessToken);
        return ResponseEntity.ok(ApiResponse.ok("Đăng xuất thành công", null));
    }
}