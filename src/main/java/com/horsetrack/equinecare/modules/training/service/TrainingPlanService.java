package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.training.dto.AvailableHorseDTO;
import com.horsetrack.equinecare.modules.training.dto.DashboardStatsDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingPlanSummaryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TrainingPlanService {
    List<AvailableHorseDTO> getAvailableHorses();
    DashboardStatsDTO getDashboardStats();
    Page<TrainingPlanSummaryDTO> getTrainingPlans(String status, Pageable pageable);
}
