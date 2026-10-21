package com.horsetrack.equinecare.modules.horse.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Hồ sơ đăng ký ngựa (bảng Horse_Registrations). Mỗi lần nộp / nộp lại là một dòng mới để giữ lịch sử.
 * PENDING_REVIEW -> APPROVED (cấp chuồng) | REJECTED (có lý do, có thể cho nộp lại) | CANCELLED.
 */
@Entity
@Table(name = "Horse_Registrations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HorseRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "registration_id")
    private Integer registrationId;

    // Cột tính toán do DB tự sinh (REG-0001), chỉ đọc
    @Column(name = "registration_code", insertable = false, updatable = false)
    private String registrationCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    // Ghi chú của chủ ngựa khi nộp / nộp lại
    @Column(name = "first_issue", length = 255)
    private String firstIssue;

    // Lý do từ chối hoặc ghi chú phê duyệt của Club Manager
    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Builder.Default
    @Column(name = "allow_resubmit", nullable = false)
    private Boolean allowResubmit = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Builder.Default
    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RegistrationCheck> checks = new ArrayList<>();
}
