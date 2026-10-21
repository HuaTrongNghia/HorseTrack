package com.horsetrack.equinecare.modules.notification.service;

import com.horsetrack.equinecare.modules.auth.security.OtpService;
import com.horsetrack.equinecare.modules.notification.exception.EmailSendException;
import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Gửi email HTML qua SMTP.
 * Chưa cấu hình MAIL_USERNAME thì chạy CHẾ ĐỘ DEV: không gửi thật, chỉ in nội dung ra console để test.
 * Gửi BẤT ĐỒNG BỘ trên luồng riêng để SMTP chậm (tới ~30 giây) không giữ request của người dùng;
 * nơi cần báo kết quả thì chờ tối đa app.mail.wait-seconds bằng {@link #await}.
 */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String smtpUsername;
    private final String fromAddress;
    private final String fromName;
    private final long otpTtlSeconds;
    private final long waitSeconds;

    /** Kết quả gửi sau khi chờ tối đa app.mail.wait-seconds. */
    public enum Delivery { SENT, FAILED, PENDING }

    // 2 luồng daemon riêng cho email: không chiếm luồng xử lý request, không chặn tắt server
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "email-sender");
        t.setDaemon(true);
        return t;
    });

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String smtpUsername,
                        @Value("${app.mail.from:}") String fromAddress,
                        @Value("${app.mail.from-name:HorseTrack EquineCare}") String fromName,
                        @Value("${app.otp.ttl-seconds:300}") long otpTtlSeconds,
                        @Value("${app.mail.wait-seconds:8}") long waitSeconds) {
        this.mailSender = mailSender;
        this.smtpUsername = smtpUsername;
        this.fromAddress = StringUtils.hasText(fromAddress) ? fromAddress : smtpUsername;
        this.fromName = fromName;
        this.otpTtlSeconds = otpTtlSeconds;
        this.waitSeconds = waitSeconds;
        if (!isConfigured()) {
            log.warn("EMAIL CHẠY CHẾ ĐỘ DEV: chưa đặt MAIL_USERNAME/MAIL_PASSWORD, email (kể cả OTP) chỉ được in ra console. KHÔNG dùng chế độ này khi triển khai thật.");
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(smtpUsername);
    }

    // ==========================================
    // MẪU EMAIL
    // ==========================================

    /** Gửi mã OTP; purposeLabel ví dụ "đặt lại mật khẩu". */
    public CompletableFuture<Void> sendOtp(String to, String fullName, String otp, String purposeLabel) {
        String body = """
                <p>Xin chào <b>%s</b>,</p>
                <p>Mã xác thực (OTP) để <b>%s</b> tài khoản HorseTrack của bạn là:</p>
                <p style="font-size:28px;font-weight:bold;letter-spacing:6px;margin:16px 0">%s</p>
                <p>Mã có hiệu lực trong <b>%s</b> và chỉ dùng được <b>một lần</b>.</p>
                <p>Nếu bạn không yêu cầu, hãy bỏ qua email này; mật khẩu của bạn không thay đổi.</p>
                """.formatted(esc(fullName), esc(purposeLabel), esc(otp), OtpService.formatDuration(otpTtlSeconds));
        return sendAsync(to, "[HorseTrack] Mã OTP " + purposeLabel, wrap(body));
    }

    /** Gửi thông tin đăng nhập ban đầu cho nhân viên do Club Manager tạo. */
    public CompletableFuture<Void> sendStaffAccount(String to, String fullName, String roleName, String username, String rawPassword) {
        String body = """
                <p>Xin chào <b>%s</b>,</p>
                <p>Quản lý CLB đã tạo tài khoản HorseTrack cho bạn với vai trò <b>%s</b>.</p>
                <table style="border-collapse:collapse;margin:12px 0">
                  <tr><td style="padding:4px 12px 4px 0">Tên đăng nhập:</td><td><b>%s</b></td></tr>
                  <tr><td style="padding:4px 12px 4px 0">Mật khẩu:</td><td><b>%s</b></td></tr>
                </table>
                <p>Vì lý do bảo mật, hãy <b>đăng nhập và đổi mật khẩu ngay</b> sau lần đăng nhập đầu tiên, và xóa email này.</p>
                """.formatted(esc(fullName), esc(roleName), esc(username), esc(rawPassword));
        return sendAsync(to, "[HorseTrack] Tài khoản nhân viên của bạn", wrap(body));
    }

    // ==========================================
    // GỬI
    // ==========================================

    /** Gửi trên luồng email; lỗi gửi làm future hoàn tất với EmailSendException. */
    public CompletableFuture<Void> sendAsync(String to, String subject, String html) {
        return CompletableFuture.runAsync(() -> send(to, subject, html), executor);
    }

    /** Chờ kết quả gửi tối đa app.mail.wait-seconds; quá thời gian thì email vẫn tiếp tục gửi ở nền (PENDING). */
    public Delivery await(CompletableFuture<Void> sending) {
        try {
            sending.get(waitSeconds, TimeUnit.SECONDS);
            return Delivery.SENT;
        } catch (TimeoutException ex) {
            return Delivery.PENDING;
        } catch (ExecutionException ex) {
            return Delivery.FAILED;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Delivery.PENDING;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    public void send(String to, String subject, String html) {
        if (!isConfigured()) {
            log.warn("""

                    ===== [DEV] EMAIL KHÔNG GỬI THẬT =====
                    To: {}
                    Subject: {}
                    {}
                    =====================================""", to, subject, html.replaceAll("<[^>]+>", " ").replaceAll("\\s{2,}", " ").trim());
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Đã gửi email '{}' tới {}", subject, to);
        } catch (Exception ex) {
            log.error("Gửi email tới {} thất bại: {}", to, ex.getMessage());
            throw new EmailSendException("Không gửi được email tới " + to + ". Vui lòng thử lại sau.", ex);
        }
    }

    private static String wrap(String body) {
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#222;max-width:560px">
                  <h2 style="color:#2f5d50;margin-bottom:8px">HorseTrack EquineCare</h2>
                  %s
                  <hr style="border:none;border-top:1px solid #ddd;margin-top:24px">
                  <p style="color:#888;font-size:12px">Email tự động, vui lòng không trả lời.</p>
                </div>
                """.formatted(body);
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
