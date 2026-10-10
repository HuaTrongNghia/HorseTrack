package com.horsetrack.equinecare.modules.training.repository;

import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Integer> {
    long countBySessionDateBetween(LocalDateTime startOfDay, LocalDateTime endOfDay);
    
    List<TrainingSession> findByAssignedTo_UserIdAndSessionDateBetweenOrderBySessionDateAsc(Integer assignedToId, LocalDateTime startDate, LocalDateTime endDate);
    
    long countByTrainingPlan_PlanIdAndStatus(Integer planId, String status);
}
