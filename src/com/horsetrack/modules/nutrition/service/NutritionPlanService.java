package com.horsetrack.equinecare.modules.nutrition.service;

import com.horsetrack.equinecare.modules.nutrition.dto.NutritionPlanCreateRequestDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionTodayItemDTO;

import java.util.List;

public interface NutritionPlanService {
    Integer createNutritionPlan(NutritionPlanCreateRequestDTO request);
    List<NutritionTodayItemDTO> getTodayNutritionPlan(Integer horseId);
}
