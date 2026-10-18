package com.horsetrack.equinecare.modules.care.repository;

import com.horsetrack.equinecare.modules.care.entity.NutritionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NutritionPlanRepository extends JpaRepository<NutritionPlan, Integer> {
    List<NutritionPlan> findByHorse_HorseId(Integer horseId);
}
