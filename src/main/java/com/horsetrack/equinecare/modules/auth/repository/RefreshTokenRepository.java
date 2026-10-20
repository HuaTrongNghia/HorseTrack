package com.horsetrack.equinecare.modules.auth.repository;

import com.horsetrack.equinecare.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Thu hồi toàn bộ refresh token của một user (dùng khi phát hiện token bị dùng lại)
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.userId = :userId AND r.revoked = false")
    int revokeAllByUserId(@Param("userId") Integer userId);
}
