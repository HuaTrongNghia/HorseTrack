package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

/**
 * Tài khoản nhân viên do Club Manager tạo: email gửi email đăng nhập + mật khẩu + nút "Xác nhận tài khoản".
 * Chưa xác nhận thì nhân viên chưa đăng nhập được, HLV / Groom chưa được phân công (ngựa, khu chuồng, buổi tập, việc chăm sóc).
 */
@Service
@RequiredArgsConstructor
public class StaffAccountService {

    /** Vai trò chỉ được phân công sau khi nhân viên xác nhận tài khoản */
    public static final Set<String> ASSIGNABLE_ROLES = Set.of("Trainer", "Groom");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";

    /** Kết quả tạo tài khoản: mật khẩu gốc và mã xác nhận chỉ dùng để gửi email, không lưu */
    public record CreatedStaff(User user, String rawPassword, String rawToken) {
    }

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditTrail auditTrail;

    @Value("${app.staff.confirm-url:http://localhost:8080/api/v1/auth/confirm-account}")
    private String confirmUrl;

    @Value("${app.staff.confirm-ttl-hours:72}")
    private long confirmTtlHours;

    /** HLV / Groom phải xác nhận tài khoản qua email trước khi được phân công */
    public static void requireConfirmed(User staff, String action) {
        if (staff != null && ASSIGNABLE_ROLES.contains(staff.getRoleName()) && staff.getEmailVerifiedAt() == null) {
            throw new IllegalArgumentException("Nhân viên " + staff.getFullName() + " chưa xác nhận tài khoản qua email, chưa thể " + action
                    + ". Nhờ nhân viên mở email và nhấn \"Xác nhận tài khoản\", hoặc gửi lại email xác nhận.");
        }
    }

    /** Gán mã xác nhận mới cho tài khoản (chưa lưu); trả về mã gốc để gửi email */
    public String newConfirmationToken(User user) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        user.setEmailVerifiedAt(null);
        user.setEmailVerifyTokenHash(sha256(token));
        user.setEmailVerifyExpiresAt(LocalDateTime.now().plusHours(confirmTtlHours));
        return token;
    }

    /** Gửi email tài khoản (email đăng nhập, mật khẩu, nút xác nhận) và chờ kết quả tối đa app.mail.wait-seconds */
    public EmailService.Delivery sendAccountEmail(CreatedStaff created) {
        User u = created.user();
        String link = confirmUrl + (confirmUrl.contains("?") ? "&" : "?") + "token=" + URLEncoder.encode(created.rawToken(), StandardCharsets.UTF_8);
        return emailService.await(emailService.sendStaffAccount(u.getEmail(), u.getFullName(), u.getRoleName(), u.getUsername(), u.getEmail(),
                created.rawPassword(), link, confirmTtlHours));
    }

    /** Thông báo cho Club Manager theo kết quả gửi email tài khoản nhân viên */
    public String emailResultMessage(EmailService.Delivery delivery, String email, String prefix) {
        return switch (delivery) {
            case SENT -> emailService.isConfigured()
                    ? prefix + " Đã gửi email xác nhận tới " + email + "."
                    : prefix + " (Chế độ dev: email chưa gửi thật, liên kết xác nhận và mật khẩu in trong console server.)";
            case PENDING -> prefix + " Email tới " + email + " đang được gửi; nếu nhân viên không nhận được sau vài phút, hãy gửi lại email xác nhận.";
            case FAILED -> prefix + " Nhưng KHÔNG gửi được email tới " + email + ". Hãy kiểm tra địa chỉ rồi gửi lại email xác nhận.";
        };
    }

    /** Nhân viên nhấn "Xác nhận tài khoản" trong email */
    @Transactional
    public User confirm(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Thiếu mã xác nhận.");
        }
        User user = userRepository.findByEmailVerifyTokenHash(sha256(token.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Liên kết xác nhận không hợp lệ hoặc đã được dùng."));
        if (user.getEmailVerifyExpiresAt() != null && user.getEmailVerifyExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Liên kết xác nhận đã hết hạn. Nhờ Quản lý CLB gửi lại email xác nhận.");
        }
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setEmailVerifyTokenHash(null);
        user.setEmailVerifyExpiresAt(null);
        userRepository.save(user);
        auditTrail.record("CONFIRM_STAFF_ACCOUNT", "Users", "email_verified=false",
                "email_verified=true | " + user.getEmail() + " (user_id=" + user.getUserId() + ", vai trò " + user.getRoleName() + ")");
        return user;
    }

    /** Club Manager gửi lại email xác nhận: tài khoản chưa đăng nhập lần nào nên cấp mật khẩu mới cùng mã mới */
    @Transactional
    public CreatedStaff resend(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + userId));
        if ("Horse Owner".equals(user.getRoleName()) || "Club Manager".equals(user.getRoleName())) {
            throw new IllegalArgumentException("Chỉ gửi lại email xác nhận cho tài khoản nhân viên.");
        }
        if (user.getEmailVerifiedAt() != null) {
            throw new IllegalArgumentException("Nhân viên " + user.getFullName() + " đã xác nhận tài khoản.");
        }
        String password = randomPassword();
        user.setPasswordHash(passwordEncoder.encode(password));
        String token = newConfirmationToken(user);
        userRepository.save(user);
        auditTrail.record("RESEND_STAFF_CONFIRM", "Users", null, "Gửi lại email xác nhận cho " + user.getEmail() + " (user_id=" + user.getUserId() + ")");
        return new CreatedStaff(user, password, token);
    }

    /** Mật khẩu tạm 12 ký tự, có chữ và số (thỏa @StrongPassword) */
    public static String randomPassword() {
        StringBuilder sb = new StringBuilder();
        String all = LETTERS + DIGITS;
        sb.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        for (int i = 0; i < 10; i++) {
            sb.append(all.charAt(RANDOM.nextInt(all.length())));
        }
        return sb.toString();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
