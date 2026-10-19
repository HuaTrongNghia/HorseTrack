package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.modules.auth.entity.RefreshToken;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Quản lý vòng đời refresh token: sinh mới, kiểm tra + xoay vòng, thu hồi.
 * Các hàm ở đây chạy trong transaction của AuthServiceImpl.
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /**
     * Sinh refresh token mới cho user, lưu mã băm vào DB và trả về token gốc cho client
     */
    public String createRefreshToken(User user) {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(hash(rawToken))
                .user(user)
                .expiresAt(now.plusNanos(refreshExpirationMs * 1_000_000))
                .revoked(false)
                .createdAt(now)
                .build());

        return rawToken;
    }

    /**
     * Kiểm tra refresh token và thu hồi nó (xoay vòng: mỗi refresh token chỉ dùng được 1 lần).
     * Nếu token đã bị thu hồi mà vẫn bị gửi lên -> nghi bị đánh cắp, thu hồi toàn bộ token của user.
     */
    public RefreshToken verifyAndRevoke(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Refresh token không hợp lệ"));

        if (Boolean.TRUE.equals(refreshToken.getRevoked())) {
            Integer userId = refreshToken.getUser().getUserId();
            log.warn("Refresh token đã thu hồi bị dùng lại, thu hồi toàn bộ phiên của userId={}", userId);
            refreshTokenRepository.revokeAllByUserId(userId);
            throw new BadCredentialsException("Refresh token đã bị thu hồi, vui lòng đăng nhập lại");
        }
        if (refreshToken.isExpired()) {
            throw new BadCredentialsException("Refresh token đã hết hạn, vui lòng đăng nhập lại");
        }

        refreshToken.setRevoked(true);
        return refreshToken;
    }

    /**
     * Thu hồi refresh token khi đăng xuất. Token không tồn tại thì bỏ qua.
     */
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(refreshToken -> refreshToken.setRevoked(true));
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationMs / 1000;
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Không hỗ trợ thuật toán SHA-256", e);
        }
    }
}
