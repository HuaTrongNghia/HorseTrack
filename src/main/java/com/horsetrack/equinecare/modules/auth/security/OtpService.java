package com.horsetrack.equinecare.modules.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mã OTP 6 chữ số gửi qua email.
 * - Dùng 1 lần: kiểm tra đúng là xóa ngay.
 * - Hết hạn sau app.otp.ttl-seconds (mặc định 120 giây).
 * - Yêu cầu mã mới thì mã cũ mất hiệu lực.
 * - Nhập sai quá app.otp.max-attempts lần thì mã bị hủy.
 * - Giữa 2 lần gửi phải cách app.otp.resend-cooldown-seconds để tránh spam email.
 * Chỉ lưu mã đã băm, trong bộ nhớ: mất khi restart server và không chia sẻ giữa nhiều instance.
 */
@Component
public class OtpService {

    public enum Purpose {
        RESET_PASSWORD("đặt lại mật khẩu"),
        CHANGE_PASSWORD("đổi mật khẩu");

        private final String label;

        Purpose(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private record Entry(String hash, Instant expiresAt, int attempts) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Entry> codes = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastIssued = new ConcurrentHashMap<>();
    private final long ttlSeconds;
    private final int maxAttempts;
    private final long cooldownSeconds;

    public OtpService(@Value("${app.otp.ttl-seconds:120}") long ttlSeconds,
                      @Value("${app.otp.max-attempts:5}") int maxAttempts,
                      @Value("${app.otp.resend-cooldown-seconds:30}") long cooldownSeconds) {
        this.ttlSeconds = ttlSeconds;
        this.maxAttempts = maxAttempts;
        this.cooldownSeconds = cooldownSeconds;
    }

    /**
     * Kiểm tra thời gian chờ giữa 2 lần gửi và ghi nhận lần gửi này.
     * Gọi cho MỌI yêu cầu (kể cả email không tồn tại) để phản hồi giống nhau, không lộ email nào đã đăng ký.
     */
    public void checkAndMarkCooldown(String email, Purpose purpose) {
        String key = key(email, purpose);
        Instant now = Instant.now();
        // compute() khóa theo key: 2 yêu cầu gửi cùng lúc không thể cùng vượt qua thời gian chờ
        long[] wait = {0};
        lastIssued.compute(key, (k, last) -> {
            if (last != null && last.plusSeconds(cooldownSeconds).isAfter(now)) {
                wait[0] = Math.max(cooldownSeconds - (now.getEpochSecond() - last.getEpochSecond()), 1);
                return last;
            }
            return now;
        });
        if (wait[0] > 0) {
            throw new IllegalArgumentException("Bạn vừa yêu cầu mã OTP. Vui lòng chờ " + wait[0] + " giây rồi thử lại.");
        }
        removeExpired(now);
    }

    /** Tạo mã mới (thay thế mã cũ nếu có) và trả về mã gốc để gửi email. */
    public String issue(String email, Purpose purpose) {
        String key = key(email, purpose);
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        codes.put(key, new Entry(hash(key, otp), Instant.now().plusSeconds(ttlSeconds), 0));
        return otp;
    }

    /** Hủy mã và thời gian chờ (dùng khi gửi email thất bại để người dùng thử lại ngay). */
    public void invalidate(String email, Purpose purpose) {
        String key = key(email, purpose);
        codes.remove(key);
        lastIssued.remove(key);
    }

    /** Kiểm tra mã; đúng thì xóa luôn (dùng 1 lần), sai thì ném lỗi 400 với thông báo phù hợp. */
    public void verify(String email, Purpose purpose, String otp) {
        String key = key(email, purpose);
        Entry entry = codes.get(key);
        if (entry == null) {
            throw new IllegalArgumentException("Mã OTP không tồn tại hoặc đã được sử dụng. Hãy yêu cầu mã mới.");
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            codes.remove(key);
            throw new IllegalArgumentException("Mã OTP đã hết hạn (hiệu lực " + ttlSeconds + " giây). Hãy yêu cầu mã mới.");
        }
        boolean matches = otp != null && MessageDigest.isEqual(
                entry.hash().getBytes(StandardCharsets.UTF_8), hash(key, otp.trim()).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            int attempts = entry.attempts() + 1;
            if (attempts >= maxAttempts) {
                codes.remove(key);
                throw new IllegalArgumentException("Nhập sai mã OTP quá " + maxAttempts + " lần. Mã đã bị hủy, hãy yêu cầu mã mới.");
            }
            codes.put(key, new Entry(entry.hash(), entry.expiresAt(), attempts));
            throw new IllegalArgumentException("Mã OTP không đúng. Bạn còn " + (maxAttempts - attempts) + " lần thử.");
        }
        codes.remove(key);
    }

    private void removeExpired(Instant now) {
        codes.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        lastIssued.entrySet().removeIf(e -> e.getValue().plusSeconds(Math.max(cooldownSeconds, ttlSeconds)).isBefore(now));
    }

    private static String key(String email, Purpose purpose) {
        return purpose.name() + ":" + email.trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String key, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((key + ":" + otp).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Không hỗ trợ thuật toán SHA-256", e);
        }
    }
}
