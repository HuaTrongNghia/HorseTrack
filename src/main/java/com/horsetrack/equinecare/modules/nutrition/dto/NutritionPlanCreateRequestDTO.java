package com.horsetrack.equinecare.modules.nutrition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionPlanCreateRequestDTO {

    @NotNull(message = "horseId không được để trống")
    private Integer horseId;

    @NotNull(message = "startDate không được để trống")
    private LocalDate startDate;

    @NotNull(message = "endDate không được để trống")
    private LocalDate endDate;

    private String notes;

    @NotEmpty(message = "Danh sách chi tiết thực đơn (details) không được để trống")
    @Valid
    private List<NutritionPlanDetailCreateDTO> details;
}