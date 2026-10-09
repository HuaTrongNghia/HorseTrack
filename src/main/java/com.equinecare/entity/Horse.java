package com.equinecare.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Horses")
public class Horse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "horse_id")
    private Integer horseId;

    @Column(name = "horse_name", nullable = false, length = 100)
    private String horseName;

    @Column(name = "owner_id", nullable = false)
    private Integer ownerId;

    @Column(name = "is_locked", nullable = false)
    private Boolean isLocked; // Cờ VetLock
}