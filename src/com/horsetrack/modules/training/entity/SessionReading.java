package com.horsetrack.equinecare.modules.training.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một lần đo trong buổi tập (SESSION) hoặc nhịp tim hồi phục khi buổi tập tạm dừng (RECOVERY) */
@Entity
@Table(name = "Session_Readings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionReading {

    public static final String PHASE_SESSION = "SESSION";
    public static final String PHASE_RECOVERY = "RECOVERY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reading_id")
    private Long readingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private TrainingSession session;

    @Column(name = "reading_phase", nullable = false, length = 10)
    private String readingPhase;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "heart_rate_bpm", nullable = false)
    private Integer heartRateBpm;

    @Column(name = "speed_kmh", nullable = false, precision = 5, scale = 2)
    private BigDecimal speedKmh;
}
