package com.horsetrack.equinecare.modules.nutrition.repository;

import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlanDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NutritionPlanDetailRepository extends JpaRepository<NutritionPlanDetail, Integer> {
    List<NutritionPlanDetail> findByNutritionPlan_PlanId(Integer planId);
}
