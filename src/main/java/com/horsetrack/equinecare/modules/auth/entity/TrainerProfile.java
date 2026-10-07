package com.horsetrack.equinecare.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Trainer_Profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrainerProfile {
    @Id
    @Column(name = "user_id")
    private Integer userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "license_number", nullable = false, length = 50)
    private String licenseNumber;

    @Column(name = "experience_years", nullable = false)
    private Integer experienceYears;
}