package com.horsetrack.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TrainingSessions")
public class TrainingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "plan_name")
    private String planName;

    @Column(name = "horse_name")
    private String horseName;

    @Column(name = "exercise_type")
    private String exerciseType;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "status", nullable = false)
    private String status = "PENDING"; // PENDING, COMPLETED, CANCELLED

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    // THÊM CÁC TRƯỜNG NÀY VÀO TRONG TRAINING SESSION
    @Column(name = "session_date")
    private java.time.LocalDateTime sessionDate;

    @Column(name = "activity_type")
    private String activityType;

    @ManyToOne
    @JoinColumn(name = "plan_id")
    private TrainingPlan trainingPlan;

    @ManyToOne
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @PreUpdate
    @PrePersist
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}