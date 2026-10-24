package com.horsetrack.equinecare.modules.training.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Một giai đoạn của giáo án: số tuần, cường độ, cự ly, số buổi/tuần, ngưỡng nhịp tim (bảng Training_Plan_Phases) */
@Entity
@Table(name = "Training_Plan_Phases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingPlanPhase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "phase_id")
    private Integer phaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private TrainingPlan plan;

    @Column(name = "phase_no", nullable = false)
    private Integer phaseNo;

    @Column(name = "phase_name", nullable = false, length = 100)
    private String phaseName;

    @Column(name = "duration_weeks", nullable = false)
    private Integer durationWeeks;

    // LOW / MEDIUM / HIGH
    @Column(name = "intensity", nullable = false, length = 10)
    private String intensity;

    @Column(name = "distance_m", precision = 10, scale = 2)
    private BigDecimal distanceM;

    @Column(name = "target_speed_kmh", precision = 5, scale = 2)
    private BigDecimal targetSpeedKmh;

    // SYNTHETIC / DIRT / TURF
    @Column(name = "track_type", length = 20)
    private String trackType;

    @Column(name = "sessions_per_week")
    private Integer sessionsPerWeek;

    // Ngưỡng nhịp tim của giai đoạn: buổi tập trong giai đoạn lấy làm ngưỡng mặc định
    @Column(name = "hr_limit_bpm")
    private Integer hrLimitBpm;
}
