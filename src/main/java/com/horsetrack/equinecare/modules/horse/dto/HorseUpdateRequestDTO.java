package com.horsetrack.equinecare.modules.horse.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorseUpdateRequestDTO {

    @DecimalMin(value = "0.1", message = "Weight must be greater than or equal to 0.1")
    private BigDecimal weightKg;

    private String healthStatus;

    private Boolean isLocked;
}
