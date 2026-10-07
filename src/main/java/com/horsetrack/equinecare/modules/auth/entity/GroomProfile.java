package com.horsetrack.equinecare.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Groom_Profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GroomProfile {
    @Id
    @Column(name = "user_id")
    private Integer userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "assigned_barn_zone", length = 50)
    private String assignedBarnZone;

    @Column(name = "shift_type", length = 20)
    private String shiftType;
}