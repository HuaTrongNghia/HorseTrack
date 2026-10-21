package com.horsetrack.equinecare.modules.nutrition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionPlanDetailCreateDTO {

    @NotNull(message = "Mã thức ăn (inventoryItemId) không được để trống")
    private Integer inventoryItemId;

    @NotBlank(message = "Loại bữa ăn (mealType) không được để trống")
    @Pattern(regexp = "(?i)^(MORNING|AFTERNOON|EVENING)$", message = "mealType phải là MORNING, AFTERNOON hoặc EVENING")
    private String mealType;

    @NotNull(message = "Định lượng (quantity) không được để trống")
    @Positive(message = "Định lượng (quantity) phải lớn hơn 0")
    private Double quantity;
}