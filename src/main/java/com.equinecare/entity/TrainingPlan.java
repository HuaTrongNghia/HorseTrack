package com.equinecare.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "Training_Plans")
public class TrainingPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Integer planId;

    @Column(name = "horse_id", nullable = false)
    private Integer horseId;

    @Column(name = "trainer_id", nullable = false)
    private Integer trainerId;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(name = "target_distance_m", nullable = false)
    private BigDecimal targetDistanceM;

    @Column(name = "target_speed_kmh", nullable = false)
    private BigDecimal targetSpeedKmh;

    @Column(name = "track_type", nullable = false, length = 50)
    private String trackType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "plan_status", nullable = false, length = 20)
    private String planStatus = "Active";
}