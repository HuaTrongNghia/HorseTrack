package com.horsetrack.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "Training_Stages")
@Data
public class TrainingStage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_id")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = false)
    private TrainingPlan trainingPlan;

    @Column(name = "stage_name")
    private String name;

    private Integer weeks;
    private String intensity;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(name = "sessions_per_week")
    private Integer sessionsPerWeek;

    @Column(name = "target_bpm")
    private Integer targetBpm;
}