package com.horsetrack.equinecare.modules.medical.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Ca khám / sự cố gửi tới hộp thư của bác sĩ thú y (bảng Vet_Cases).
 * Luồng trạng thái: NEW -> IN_EXAM / MONITORING -> CLOSED (hoặc CANCELLED).
 */
@Entity
@Table(name = "Vet_Cases")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VetCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id")
    private Integer caseId;

    // Cột tính toán do DB tự sinh (C-0001), chỉ đọc
    @Column(name = "case_code", insertable = false, updatable = false)
    private String caseCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by", nullable = false)
    private User reportedBy;

    @Column(name = "case_type", nullable = false, length = 50)
    private String caseType;

    @Column(name = "description", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(name = "reported_at", nullable = false, updatable = false)
    private LocalDateTime reportedAt;

    @Column(name = "source", nullable = false, length = 20)
    private String source; // GROOM_REPORT, TRAINER_REQUEST, SESSION_STOP, OWNER_REQUEST, ROUTINE

    @Column(name = "training_day_id")
    private Integer trainingDayId;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // NEW, IN_EXAM, MONITORING, CLOSED, CANCELLED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_vet_id")
    private User assignedVet;

    @Column(name = "outcome", length = 1000)
    private String outcome;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
