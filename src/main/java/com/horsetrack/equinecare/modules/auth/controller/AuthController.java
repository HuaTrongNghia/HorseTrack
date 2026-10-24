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
    private final com.horsetrack.equinecare.modules.auth.service.StaffAccountService staffAccountService;

    // Có giá trị thì sau khi nhân viên xác nhận, chuyển về trang đăng nhập của FE (kèm ?confirmed=&message=)
    @org.springframework.beans.factory.annotation.Value("${app.staff.login-url:}")
    private String staffLoginUrl;

    public AuthController(AuthService authService, EmailService emailService, OtpService otpService,
                          com.horsetrack.equinecare.modules.auth.service.StaffAccountService staffAccountService) {
        this.authService = authService;
        this.emailService = emailService;
        this.otpService = otpService;
        this.staffAccountService = staffAccountService;
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
    @Operation(summary = "Quản lý tạo nhân viên", description = "Chỉ dành cho Club Manager. Tạo xong hệ thống gửi email gồm email đăng nhập, mật khẩu và nút \"Xác nhận tài khoản\". "
            + "Nhân viên chưa xác nhận thì chưa đăng nhập được; HLV / Groom chưa xác nhận thì chưa được phân công.")
    public ResponseEntity<ApiResponse<Void>> createStaff(@Valid @RequestBody StaffCreateRequestDTO request) {
        com.horsetrack.equinecare.modules.auth.service.StaffAccountService.CreatedStaff created;
        try {
            // Lưu tài khoản trước (transaction riêng); email chỉ gửi khi đã lưu thành công
            created = authService.createStaff(request);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Không thể hoàn tất tạo tài khoản: " + ex.getMessage()));
        }

        // Gửi email thất bại không hủy tài khoản đã tạo; báo để Club Manager gửi thông tin cho nhân viên theo cách khác
        // Email gửi ở nền; chờ tối đa app.mail.wait-seconds để báo kết quả, quá hạn thì vẫn tiếp tục gửi
        String email = created.user().getEmail();
        String message = staffAccountService.emailResultMessage(staffAccountService.sendAccountEmail(created), email,
                "Tạo tài khoản nhân viên thành công! Nhân viên cần nhấn \"Xác nhận tài khoản\" trong email trước khi đăng nhập / được phân công.");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(message, null));
    }

    // Nhân viên nhấn nút "Xác nhận tài khoản" trong email (mở trên trình duyệt)
    @GetMapping(value = "/confirm-account", produces = org.springframework.http.MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Nhân viên xác nhận tài khoản (liên kết trong email)", description = "Trả trang thông báo; đặt STAFF_LOGIN_URL thì chuyển về trang đăng nhập của FE.")
    public ResponseEntity<String> confirmAccountPage(@RequestParam(required = false) String token) {
        boolean ok;
        String text;
        try {
            var user = staffAccountService.confirm(token);
            ok = true;
            text = "Tài khoản " + user.getEmail() + " đã được xác nhận. Bạn có thể đăng nhập HorseTrack bằng email và mật khẩu trong email.";
        } catch (IllegalArgumentException ex) {
            ok = false;
            text = ex.getMessage();
        }
        if (org.springframework.util.StringUtils.hasText(staffLoginUrl)) {
            String target = staffLoginUrl + (staffLoginUrl.contains("?") ? "&" : "?") + "confirmed=" + ok
                    + "&message=" + java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.FOUND).header(org.springframework.http.HttpHeaders.LOCATION, target).build();
        }
        String html = "<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>HorseTrack - Xác nhận tài khoản</title></head><body style=\"font-family:Arial,sans-serif;max-width:520px;margin:60px auto;padding:0 16px\">"
                + "<h2 style=\"color:" + (ok ? "#1b4332" : "#b42318") + "\">" + (ok ? "Xác nhận thành công" : "Không xác nhận được") + "</h2>"
                + "<p>" + org.springframework.web.util.HtmlUtils.htmlEscape(text) + "</p></body></html>";
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).contentType(org.springframework.http.MediaType.TEXT_HTML).body(html);
    }

    // Trang xác nhận của FE gửi mã lên (JSON)
    @PostMapping("/confirm-account")
    @Operation(summary = "Nhân viên xác nhận tài khoản (FE gửi mã)", description = "Body: { \"token\": \"...\" } lấy từ liên kết trong email.")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> confirmAccount(@RequestBody java.util.Map<String, String> body) {
        try {
            var user = staffAccountService.confirm(body == null ? null : body.get("token"));
            return ResponseEntity.ok(ApiResponse.ok("Đã xác nhận tài khoản. Đăng nhập bằng email và mật khẩu trong email.",
                    java.util.Map.of("email", user.getEmail(), "roleName", user.getRoleName())));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
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