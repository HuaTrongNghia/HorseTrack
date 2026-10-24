package com.horsetrack.equinecare.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "username", nullable = false, length = 50, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, length = 100, unique = true)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "role_name", nullable = false, length = 30)
    private String roleName;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Xác nhận tài khoản qua email (nhân viên do Club Manager tạo); NULL = chưa xác nhận
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    // SHA-256 của mã xác nhận trong email (không lưu mã gốc)
    @Column(name = "email_verify_token_hash", length = 64)
    private String emailVerifyTokenHash;

    @Column(name = "email_verify_expires_at")
    private LocalDateTime emailVerifyExpiresAt;

    // =========================================================================
    // THIẾT LẬP QUAN HỆ 1-1 VỚI CÁC BẢNG HỒ SƠ CHUYÊN MÔN (PROFILES)
    // =========================================================================

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private HorseOwnerProfile horseOwnerProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ClubManagerProfile clubManagerProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private TrainerProfile trainerProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private VeterinarianProfile veterinarianProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private GroomProfile groomProfile;
}