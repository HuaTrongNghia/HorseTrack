package com.horsetrack.equinecare.modules.training.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Training_Sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Integer sessionId;

    // QA H3: khóa lạc quan - hai thao tác đồng thời trên cùng dòng thì thao tác sau bị từ chối (409) thay vì ghi đè
    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "session_date", nullable = false)
    private LocalDateTime sessionDate;

    @Column(name = "activity_type", nullable = false, length = 100)
    private String activityType;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    // Ngưỡng của buổi tập (F22-02): chỉ số vượt ngưỡng thì buổi tập tự tạm dừng chờ HLV
    @Column(name = "max_heart_rate")
    private Integer maxHeartRate;

    @Column(name = "max_speed_kmh", precision = 5, scale = 2)
    private java.math.BigDecimal maxSpeedKmh;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "paused_reason", length = 500)
    private String pausedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private TrainingPlan trainingPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;
}
