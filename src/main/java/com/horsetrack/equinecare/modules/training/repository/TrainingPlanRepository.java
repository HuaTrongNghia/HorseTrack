package com.horsetrack.equinecare.modules.training.repository;

import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Integer> {
    Page<TrainingPlan> findAll(Pageable pageable);
    Page<TrainingPlan> findByStatus(String status, Pageable pageable);
    long countByStatus(String status);
    Optional<TrainingPlan> findFirstByHorse_HorseIdAndStatusOrderByCreatedAtDesc(Integer horseId, String status);
}
