package com.horsetrack.equinecare.modules.nutrition.repository;

import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface NutritionPlanRepository extends JpaRepository<NutritionPlan, Integer> {

    @Query("SELECT DISTINCT np FROM NutritionPlan np " +
            "LEFT JOIN FETCH np.details d " +
            "LEFT JOIN FETCH d.inventoryItem " +
            "WHERE np.horse.horseId = :horseId " +
            "AND np.startDate <= :today AND np.endDate >= :today " +
            "ORDER BY np.createdDate DESC, np.planId DESC")
    List<NutritionPlan> findActivePlansForHorseOnDate(@Param("horseId") Integer horseId, @Param("today") LocalDate today);

    List<NutritionPlan> findByHorse_HorseIdOrderByCreatedDateDesc(Integer horseId);
}
