package com.horsetrack.equinecare.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Horse_Owner_Profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HorseOwnerProfile {
    @Id
    @Column(name = "user_id")
    private Integer userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "tax_code", length = 30)
    private String taxCode;

    @Column(name = "billing_address", nullable = false, length = 255)
    private String billingAddress;
}