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

    @Column(name = "notes", columnDefinition = "NVARCHAR(255)")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private MedicalRecord medicalRecord;
}
