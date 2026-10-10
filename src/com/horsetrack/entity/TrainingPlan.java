package com.horsetrack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "Training_Plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrainingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Integer id;

    @Column(name = "horse_id", nullable = false)
    private Integer horseId;

    @Column(name = "trainer_id")
    private Integer trainerId;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(name = "target_distance_m")
    private Double targetDistanceM;

    @Column(name = "target_speed_kmh")
    private Double targetSpeedKmh;

    @Column(name = "track_type", length = 50)
    private String trackType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "plan_status", length = 20)
    private String planStatus;
}