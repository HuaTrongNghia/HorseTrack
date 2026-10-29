package com.horsetrack.equinecare.modules.nutrition.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionPlanResponseDTO {

    private Integer planId;
    private Integer horseId;
    private String horseName;
    private Integer createdById;
    private String createdByName;
    private LocalDateTime createdDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String planType;
    private String status;
    private String notes;
    private String specialInstructions;
    private List<NutritionPlanDetailDTO> details;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NutritionPlanDetailDTO {
        private Integer detailId;
        private Integer inventoryItemId;
        private String itemCode;
        private String itemName;
        private String unit;
        private String mealType;
        private double quantity;
    }
}
