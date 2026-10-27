package com.horsetrack.equinecare.modules.training.service.impl;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.training.dto.OwnerHorseListDTO;
import com.horsetrack.equinecare.modules.training.dto.OwnerTrainingProgressDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingSessionLogDTO;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import com.horsetrack.equinecare.modules.training.repository.TrainingPlanRepository;
import com.horsetrack.equinecare.modules.training.service.OwnerTrainingService;
import com.horsetrack.equinecare.modules.training.service.SessionEvaluationHelper;
import com.horsetrack.equinecare.modules.training.service.TrainingPlanActivator;
import com.horsetrack.equinecare.modules.training.service.TrainingPlanPhases;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerTrainingServiceImpl implements OwnerTrainingService {

    private final HorseRepository horseRepository;
    private final TrainingPlanRepository trainingPlanRepository;
    private final UserRepository userRepository;

    private User getLoggedInUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OwnerHorseListDTO> getMyHorses() {
        User user = getLoggedInUser();
        List<Horse> horses = horseRepository.findByOwnerUserId(user.getUserId());
        return horses.stream()
                .map(horse -> OwnerHorseListDTO.builder()
                        .horseId(horse.getHorseId())
                        .horseName(horse.getHorseName())
                        .approvalStatus(horse.getApprovalStatus())
                        .stallId(horse.getStall() != null ? horse.getStall().getStallId() : null)
                        .stallCode(horse.getStall() != null ? horse.getStall().getStallCode() : null)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerTrainingProgressDTO getTrainingProgress(Integer horseId) {
        User user = getLoggedInUser();
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new RuntimeException("Horse not found"));

        if (!horse.getOwner().getUserId().equals(user.getUserId())) {
            throw new AccessDeniedException("You do not have permission to view this horse's training progress");
        }

        List<TrainingPlan> plans = trainingPlanRepository.findByHorse_HorseIdOrderByCreatedAtDesc(horseId);
        TrainingPlan plan = plans.stream()
                .filter(p -> TrainingPlanActivator.OPEN_PLAN_STATUSES.contains(String.valueOf(p.getStatus()).toUpperCase()))
                .findFirst()
                .orElse(plans.isEmpty() ? null : plans.get(0));

        if (plan == null) {
            return OwnerTrainingProgressDTO.builder()
                    .planId(null)
                    .planName(null)
                    .startDate(null)
                    .endDate(null)
                    .status("NONE")
                    .progress(0.0)
                    .sessions(List.of())
                    .phases(List.of())
                    .build();
        }

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        List<TrainingSessionLogDTO> sessionDTOs = new ArrayList<>();
        for (TrainingPlan each : plans) {
            if (each.getSessions() == null) {
                continue;
            }
            for (TrainingSession s : each.getSessions()) {
                SessionEvaluationHelper.EvaluationData eval = SessionEvaluationHelper.extractEvaluation(s.getNotes());
                boolean late = "PENDING".equalsIgnoreCase(s.getStatus()) && s.getSessionDate() != null && s.getSessionDate().isBefore(startOfToday);
                sessionDTOs.add(TrainingSessionLogDTO.builder()
                        .sessionId(s.getSessionId())
                        .sessionDate(s.getSessionDate())
                        .activityType(s.getActivityType())
                        .status(s.getStatus())
                        .notes(SessionEvaluationHelper.removeEvaluation(s.getNotes()))
                        .assignedToName(s.getAssignedTo() != null ? s.getAssignedTo().getFullName() : null)
                        .evaluationScore(eval != null ? eval.getScore() : null)
                        .evaluationComment(eval != null ? eval.getComment() : null)
                        .evaluatedBy(eval != null ? eval.getBy() : null)
                        .evaluatedAt(eval != null ? eval.getAt() : null)
                        .planName(each.getPlanName())
                        .late(late)
                        .build());
            }
        }
        sessionDTOs.sort(Comparator.comparing(TrainingSessionLogDTO::getSessionDate, Comparator.nullsLast(Comparator.reverseOrder())));

        List<TrainingSession> currentSessions = plan.getSessions() == null ? List.of() : plan.getSessions();
        double progress = 0.0;
        if (!currentSessions.isEmpty()) {
            long completed = currentSessions.stream().filter(s -> "COMPLETED".equalsIgnoreCase(s.getStatus())).count();
            progress = (double) completed / currentSessions.size() * 100.0;
        }
        String delay = plan.getPausedReason() != null && plan.getPausedReason().startsWith("Trễ:") ? plan.getPausedReason() : null;

        return OwnerTrainingProgressDTO.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .startDate(plan.getStartDate())
                .endDate(plan.getEndDate())
                .status(plan.getStatus())
                .progress(progress)
                .delayNote(delay)
                .phases(TrainingPlanPhases.toDtos(plan))
                .sessions(sessionDTOs)
                .build();
    }
}
