package com.horsetrack.equinecare.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Veterinarian_Profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VeterinarianProfile {
    @Id
    @Column(name = "user_id")
    private Integer userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "vet_license_no", nullable = false, length = 50)
    private String vetLicenseNo;

    @Column(name = "clinic_affiliation", length = 150)
    private String clinicAffiliation;
}