package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.training.dto.AvailableHorseDTO;
import com.horsetrack.equinecare.modules.training.dto.DashboardStatsDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingPlanSummaryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.horsetrack.equinecare.modules.training.dto.TrainingPlanDetailDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingSessionDTO;
import java.util.List;

public interface TrainingPlanService {
    List<AvailableHorseDTO> getAvailableHorses();
    DashboardStatsDTO getDashboardStats();
    Page<TrainingPlanSummaryDTO> getTrainingPlans(String status, Pageable pageable);
    List<com.horsetrack.equinecare.modules.training.dto.StaffAssignDTO> getAssignableStaff();
    void addTrainingSessionToPlan(Integer planId, com.horsetrack.equinecare.modules.training.dto.TrainingSessionCreateRequestDTO request);
    TrainingPlanDetailDTO getTrainingPlanDetail(Integer planId);
}

