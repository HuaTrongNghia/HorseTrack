package com.horsetrack.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Horse_Owner_Profiles")
public class HorseOwnerProfile {
    @Id
    @Column(name = "user_id")
    private Integer userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "tax_code")
    private String taxCode;

    @Column(name = "billing_address", nullable = false)
    private String billingAddress;
}