package com.horsetrack.equinecare.modules.horse.entity;

import com.horsetrack.equinecare.common.enums.HealthStatus;
import com.horsetrack.equinecare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Horses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Horse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "horse_id")
    private Integer horseId;

    @Column(name = "horse_name", nullable = false, length = 100)
    private String horseName;

    @Column(name = "dob", nullable = false)
    private LocalDate dob;

    @Column(name = "gender", nullable = false, length = 10)
    private String gender;

    @Column(name = "breed", nullable = false, length = 50)
    private String breed;

    @Column(name = "pedigree", length = 255)
    private String pedigree;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 20)
    private HealthStatus healthStatus;

    @Column(name = "is_locked", nullable = false)
    private Boolean isLocked;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stall_id", unique = true)
    private Stall stall;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Boolean getLocked() {
        return isLocked;
    }

    public void setLocked(Boolean locked) {
        isLocked = locked;
    }
}