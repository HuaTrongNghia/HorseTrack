package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.training.dto.AvailableHorseDTO;
import com.horsetrack.equinecare.modules.training.dto.DashboardStatsDTO;
import com.horsetrack.equinecare.modules.training.dto.ManagerTodayOverviewDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingPlanSummaryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.horsetrack.equinecare.modules.training.dto.StaffAssignDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingPlanDetailDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingSessionCreateRequestDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingPlanCreateRequestDTO;

import java.util.List;

public interface TrainingPlanService {
    List<AvailableHorseDTO> getAvailableHorses();
    DashboardStatsDTO getDashboardStats();
    ManagerTodayOverviewDTO getManagerTodayOverview();
    Page<TrainingPlanSummaryDTO> getTrainingPlans(String status, Pageable pageable);
    /** Groom đang hoạt động cùng khu chuồng với ngựa của giáo án (SRS: chọn khu → ngựa → Groom của khu đó). */
    List<StaffAssignDTO> getAssignableStaff(Integer planId);
    TrainingPlanDetailDTO getTrainingPlanDetail(Integer planId);
    void addTrainingSessionToPlan(Integer planId, TrainingSessionCreateRequestDTO request);

    Integer createTrainingPlan(TrainingPlanCreateRequestDTO request);
}

