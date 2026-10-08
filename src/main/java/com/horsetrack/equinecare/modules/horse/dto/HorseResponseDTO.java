package com.horsetrack.equinecare.modules.horse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorseResponseDTO {

    private Integer horseId;
    private String horseName;
    private LocalDate dob;
    private String gender;
    private String breed;
    private String pedigree;
    private BigDecimal weightKg;
    private String healthStatus;
    private Boolean isLocked;
    private BigDecimal prizeSharePct;
    private Integer ownerId;
    private Integer stallId;
    private LocalDateTime createdAt;
}
