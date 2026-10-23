package com.horsetrack.equinecare.modules.medical.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Injury_Marks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InjuryMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mark_id")
    private Integer markId;

    @Column(name = "affected_body_part", nullable = false, length = 100)
    private String affectedBodyPart;

    @Column(name = "severity_level", nullable = false, length = 20)
    private String severityLevel;

    @Column(name = "coord_x")
    private Double coordX;

    @Column(name = "coord_y")
    private Double coordY;

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    // Chấn thương ghi trong bệnh án (cũ) hoặc đánh dấu trực tiếp trên ngựa (POST /horses/{id}/injuries)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id")
    private MedicalRecord medicalRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id")
    private com.horsetrack.equinecare.modules.horse.entity.Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private VetCase vetCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private com.horsetrack.equinecare.modules.auth.entity.User createdBy;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;
}
