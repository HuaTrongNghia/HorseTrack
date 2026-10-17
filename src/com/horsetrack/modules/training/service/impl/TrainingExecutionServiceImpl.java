package com.horsetrack.equinecare.modules.training.service.impl;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.training.dto.SessionExecuteRequestDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainerScheduleDTO;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import com.horsetrack.equinecare.modules.training.repository.TrainingPlanRepository;
import com.horsetrack.equinecare.modules.training.repository.TrainingSessionRepository;
import com.horsetrack.equinecare.modules.training.service.TrainingExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingExecutionServiceImpl implements TrainingExecutionService {

    private final TrainingSessionRepository trainingSessionRepository;
    private final TrainingPlanRepository trainingPlanRepository;
    private final UserRepository userRepository;

    private User getLoggedInUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainerScheduleDTO> getMySchedule(LocalDate startDate, LocalDate endDate) {
        User user = getLoggedInUser();
        LocalDateTime startOfDay = startDate.atStartOfDay();
        LocalDateTime endOfDay = endDate.plusDays(1).atStartOfDay().minusNanos(1);

        List<TrainingSession> sessions = trainingSessionRepository.findByAssignedTo_UserIdAndSessionDateBetweenOrderBySessionDateAsc(
                user.getUserId(), startOfDay, endOfDay);

        return sessions.stream().map(session -> {
            String horseName = null;
            String stallZone = null;
            String planName = null;
            
            if (session.getTrainingPlan() != null) {
                planName = session.getTrainingPlan().getPlanName();
                if (session.getTrainingPlan().getHorse() != null) {
                    horseName = session.getTrainingPlan().getHorse().getHorseName();
                    if (session.getTrainingPlan().getHorse().getStall() != null) {
                        stallZone = session.getTrainingPlan().getHorse().getStall().getBarnZone();
                    }
                }
            }

            return TrainerScheduleDTO.builder()
                    .sessionId(session.getSessionId())
                    .sessionDate(session.getSessionDate())
                    .activityType(session.getActivityType())
                    .status(session.getStatus())
                    .planName(planName)
                    .horseName(horseName)
                    .stallZone(stallZone)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void executeSession(Integer sessionId, SessionExecuteRequestDTO request) {
        User user = getLoggedInUser();
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getAssignedTo() == null || !session.getAssignedTo().getUserId().equals(user.getUserId())) {
            throw new AccessDeniedException("You are not assigned to this session");
        }

        if (!"PENDING".equalsIgnoreCase(session.getStatus())) {
            throw new RuntimeException("Cannot update session. Current status is " + session.getStatus());
        }

        if ("CANCELLED".equalsIgnoreCase(request.getStatus()) && (request.getNotes() == null || request.getNotes().trim().isEmpty())) {
            throw new RuntimeException("Notes are required when cancelling a session");
        }

        session.setStatus(request.getStatus().toUpperCase());
        session.setNotes(request.getNotes());
        trainingSessionRepository.save(session);

        if ("COMPLETED".equalsIgnoreCase(request.getStatus())) {
            TrainingPlan plan = session.getTrainingPlan();
            if (plan != null) {
                long pendingCount = trainingSessionRepository.countByTrainingPlan_PlanIdAndStatus(plan.getPlanId(), "PENDING");
                if (pendingCount == 0) {
                    plan.setStatus("COMPLETED");
                    trainingPlanRepository.save(plan);
                }
            }
        }
    }
}
