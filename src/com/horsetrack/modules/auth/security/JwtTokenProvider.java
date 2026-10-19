package com.horsetrack.equinecare.modules.auth.security;

import com.horsetrack.equinecare.modules.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long jwtExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long jwtExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMs = jwtExpirationMs;
    }

    /**
     * Sinh chuỗi JWT Token chứa các thông tin Claims bắt buộc:
     * - sub (Subject): username của người dùng
     * - userId: ID của người dùng
     * - role: Tên chức danh/vai trò của người dùng (VD: "Club Manager", "Head Trainer", "Groom"...)
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getUserId())
                .claim("role", user.getRoleName())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key) // Tự động sử dụng thuật toán HMAC-SHA256 (HS256) tương ứng với SecretKey
                .compact();
    }

    /**
     * Thời gian sống của token (giây), dùng cho trường expiresIn khi đăng nhập
     */
    public long getExpirationSeconds() {
        return jwtExpirationMs / 1000;
    }

    /**
     * Trích xuất username (Subject) từ JWT Token
     */
    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Trích xuất userId từ Claims trong JWT Token
     */
    public Integer getUserIdFromToken(String token) {
        return getClaims(token).get("userId", Integer.class);
    }

    /**
     * Trích xuất role từ Claims trong JWT Token
     */
    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    /**
     * Kiểm tra tính hợp lệ và thời hạn của JWT Token
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT Token không hợp lệ hoặc đã hết hạn: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Giải mã và đọc Claims từ token đã được ký
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
