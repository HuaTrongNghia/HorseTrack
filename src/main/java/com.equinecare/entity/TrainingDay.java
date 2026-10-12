package com.equinecare.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "Training_Days")
public class TrainingDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "training_day_id")
    private Integer trainingDayId;

    @Column(name = "plan_id", nullable = false)
    private Integer planId;

    @Column(name = "trainer_id", nullable = false)
    private Integer trainerId;

    @Column(name = "groom_id")
    private Integer groomId;

    @Column(name = "training_date", nullable = false)
    private LocalDate trainingDate;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 60;

    @Column(name = "avg_heart_rate", nullable = false)
    private BigDecimal avgHeartRate = BigDecimal.ZERO;

    @Column(name = "max_heart_rate", nullable = false)
    private BigDecimal maxHeartRate = BigDecimal.ZERO;

    @Column(name = "avg_speed_kmh", nullable = false)
    private BigDecimal avgSpeedKmh = BigDecimal.ZERO;

    @Column(name = "is_over_threshold", nullable = false)
    private Boolean isOverThreshold = false;

    @Column(name = "training_fee", nullable = false)
    private BigDecimal trainingFee = BigDecimal.ZERO;
}