package com.horsetrack.equinecare.modules.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SafetyLevelRequestDTO {

    @NotNull(message = "Mức tồn kho an toàn (minSafetyStock) là bắt buộc")
    @DecimalMin(value = "0", message = "Mức tồn kho an toàn không được âm")
    @Digits(integer = 8, fraction = 2, message = "Mức tồn kho an toàn tối đa 2 chữ số thập phân")
    private Double minSafetyStock;
}
