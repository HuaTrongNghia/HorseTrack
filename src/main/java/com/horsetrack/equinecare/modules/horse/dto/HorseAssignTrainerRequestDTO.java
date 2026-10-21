package com.horsetrack.equinecare.modules.horse.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HorseAssignTrainerRequestDTO {

    @NotNull(message = "trainerId không được để trống")
    private Integer trainerId;
}
