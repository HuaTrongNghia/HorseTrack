package com.horsetrack.equinecare.modules.nutrition.service;

import com.horsetrack.equinecare.modules.inventory.dto.InventoryItemDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionPlanCreateRequestDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionPlanResponseDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionTodayItemDTO;

import java.util.List;

public interface NutritionPlanService {
    Integer createNutritionPlan(NutritionPlanCreateRequestDTO request);
    List<NutritionTodayItemDTO> getTodayNutritionPlan(Integer horseId);
    List<NutritionPlanResponseDTO> getNutritionPlans(Integer horseId, String status);
    NutritionPlanResponseDTO getNutritionPlanDetail(Integer planId);
    NutritionPlanResponseDTO updateNutritionPlan(Integer planId, NutritionPlanCreateRequestDTO request);
    void cancelNutritionPlan(Integer planId);
    void deleteNutritionPlan(Integer planId);
    List<InventoryItemDTO> getFeedItems();

    // Gửi khẩu phần thành việc cho ăn cho Groom của khu chuồng; trả về số việc vừa tạo
    int sendFeedTasks(Integer planId, java.time.LocalDate date);
}
