package com.horsetrack.equinecare.modules.auth.security;

import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Danh sách access token đã đăng xuất (lưu theo jti cho tới khi token tự hết hạn).
 * Lưu trong bộ nhớ: mất khi restart server và không chia sẻ giữa nhiều instance.
 */
@Component
public class TokenBlacklistService {

    private final Map<String, Date> revokedTokens = new ConcurrentHashMap<>();

    public void revoke(String jti, Date expiresAt) {
        removeExpired();
        revokedTokens.put(jti, expiresAt);
    }

    public boolean isRevoked(String jti) {
        return jti != null && revokedTokens.containsKey(jti);
    }

    // Token đã hết hạn thì JWT tự bị từ chối, không cần giữ trong danh sách nữa
    private void removeExpired() {
        Date now = new Date();
        revokedTokens.values().removeIf(expiresAt -> expiresAt.before(now));
    }
}
