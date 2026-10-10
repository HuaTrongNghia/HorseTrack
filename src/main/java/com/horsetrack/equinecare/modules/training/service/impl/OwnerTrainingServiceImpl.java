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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        TrainingPlan plan = trainingPlanRepository.findFirstByHorse_HorseIdAndStatusOrderByCreatedAtDesc(horseId, "ACTIVE")
                .orElseThrow(() -> new RuntimeException("Ngựa hiện chưa có giáo án nào"));

        List<TrainingSession> sessions = plan.getSessions();
        double progress = 0.0;
        List<TrainingSessionLogDTO> sessionDTOs = List.of();

        if (sessions != null && !sessions.isEmpty()) {
            long completed = sessions.stream()
                    .filter(s -> "COMPLETED".equalsIgnoreCase(s.getStatus()))
                    .count();
            progress = (double) completed / sessions.size() * 100.0;

            sessionDTOs = sessions.stream()
                    .sorted(Comparator.comparing(TrainingSession::getSessionDate))
                    .map(s -> TrainingSessionLogDTO.builder()
                            .sessionId(s.getSessionId())
                            .sessionDate(s.getSessionDate())
                            .activityType(s.getActivityType())
                            .status(s.getStatus())
                            .notes(s.getNotes())
                            .assignedToName(s.getAssignedTo() != null ? s.getAssignedTo().getFullName() : null)
                            .build())
                    .collect(Collectors.toList());
        }

        return OwnerTrainingProgressDTO.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .startDate(plan.getStartDate())
                .endDate(plan.getEndDate())
                .status(plan.getStatus())
                .progress(progress)
                .sessions(sessionDTOs)
                .build();
    }
}
