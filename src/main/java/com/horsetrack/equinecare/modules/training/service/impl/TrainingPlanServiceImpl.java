package com.horsetrack.equinecare.modules.training.service.impl;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.training.dto.AvailableHorseDTO;
import com.horsetrack.equinecare.modules.training.dto.DashboardStatsDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingPlanSummaryDTO;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import com.horsetrack.equinecare.modules.training.repository.TrainingPlanRepository;
import com.horsetrack.equinecare.modules.training.repository.TrainingSessionRepository;
import com.horsetrack.equinecare.modules.training.service.TrainingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingPlanServiceImpl implements TrainingPlanService {

    private final TrainingPlanRepository trainingPlanRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final HorseRepository horseRepository;

    @Override
    public List<AvailableHorseDTO> getAvailableHorses() {
        List<Horse> availableHorses = horseRepository.findAvailableHorses();
        return availableHorses.stream()
                .map(horse -> AvailableHorseDTO.builder()
                        .horseId(horse.getHorseId())
                        .horseName(horse.getHorseName())
                        .breed(horse.getBreed())
                        .weightKg(horse.getWeightKg())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public DashboardStatsDTO getDashboardStats() {
        long totalActivePlans = trainingPlanRepository.countByStatus("ACTIVE");
        
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
        long todaysSessions = trainingSessionRepository.countBySessionDateBetween(startOfDay, endOfDay);
        
        long injuredHorses = horseRepository.countByHealthStatus("Injured");

        return DashboardStatsDTO.builder()
                .totalActivePlans(totalActivePlans)
                .todaysSessions(todaysSessions)
                .injuredHorses(injuredHorses)
                .build();
    }

    @Override
    public Page<TrainingPlanSummaryDTO> getTrainingPlans(String status, Pageable pageable) {
        Page<TrainingPlan> plansPage;
        if (status != null && !status.trim().isEmpty()) {
            plansPage = trainingPlanRepository.findByStatus(status, pageable);
        } else {
            plansPage = trainingPlanRepository.findAll(pageable);
        }

        return plansPage.map(plan -> {
            List<TrainingSession> sessions = plan.getSessions();
            double progress = 0.0;
            if (sessions != null && !sessions.isEmpty()) {
                long completed = sessions.stream()
                        .filter(s -> "COMPLETED".equalsIgnoreCase(s.getStatus()))
                        .count();
                // Calculate percentage (0.0 to 100.0)
                progress = (double) completed / sessions.size() * 100.0;
            }
            
            return TrainingPlanSummaryDTO.builder()
                    .planId(plan.getPlanId())
                    .planName(plan.getPlanName())
                    .horseName(plan.getHorse() != null ? plan.getHorse().getHorseName() : null)
                    .startDate(plan.getStartDate())
                    .endDate(plan.getEndDate())
                    .status(plan.getStatus())
                    .progress(progress)
                    .build();
        });
    }
}
