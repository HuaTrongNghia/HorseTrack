package com.horsetrack.equinecare.modules.horse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Stalls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stall_id")
    private Integer stallId;

    @Column(name = "stall_code", nullable = false, length = 20, unique = true)
    private String stallCode;

    @Column(name = "barn_zone", nullable = false, length = 50)
    private String barnZone;

    @Column(name = "clean_status", nullable = false, length = 30)
    private String cleanStatus;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}