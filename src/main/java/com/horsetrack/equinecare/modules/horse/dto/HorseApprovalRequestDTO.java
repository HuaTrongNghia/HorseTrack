package com.horsetrack.equinecare.modules.horse.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorseApprovalRequestDTO {
    
    @NotNull(message = "Stall ID is required")
    private Integer stallId;
}
