package com.horsetrack.equinecare.modules.nutrition.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionTodayItemDTO {
    private String mealType;
    private String inventoryItemName;
    private Double quantity;
    private Integer inventoryItemId;
}
