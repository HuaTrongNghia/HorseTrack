package com.horsetrack.equinecare.modules.training.service.impl;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.training.dto.*;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import com.horsetrack.equinecare.modules.training.repository.TrainingPlanRepository;
import com.horsetrack.equinecare.modules.training.repository.TrainingSessionRepository;
import com.horsetrack.equinecare.modules.training.service.TrainingPlanService;
import lombok.RequiredArgsConstructor;
import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingPlanServiceImpl implements TrainingPlanService {

    private final TrainingPlanRepository trainingPlanRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final HorseRepository horseRepository;
    private final UserRepository userRepository;

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

    @Override
    @Transactional(readOnly = true)
    public List<StaffAssignDTO> getAssignableStaff() {
        List<User> staffList = userRepository.findByRoleNameInAndIsActiveTrue(Arrays.asList("Groom", "Trainer"));
        return staffList.stream()
                .map(user -> StaffAssignDTO.builder()
                        .userId(user.getUserId())
                        .fullName(user.getFullName())
                        .roleName(user.getRoleName())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TrainingPlanDetailDTO getTrainingPlanDetail(Integer planId) {
        TrainingPlan plan = trainingPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Giáo án với ID: " + planId));

        List<TrainingSessionDTO> sessionDTOs = plan.getSessions().stream()
                .map(s -> TrainingSessionDTO.builder()
                        .sessionId(s.getSessionId())
                        .sessionDate(s.getSessionDate())
                        .activityType(s.getActivityType())
                        .status(s.getStatus())
                        .notes(s.getNotes())
                        .assignedToId(s.getAssignedTo() != null ? s.getAssignedTo().getUserId() : null)
                        .assignedToName(s.getAssignedTo() != null ? s.getAssignedTo().getFullName() : null)
                        .build())
                .collect(Collectors.toList());

        return TrainingPlanDetailDTO.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .horseId(plan.getHorse() != null ? plan.getHorse().getHorseId() : null)
                .horseName(plan.getHorse() != null ? plan.getHorse().getHorseName() : null)
                .startDate(plan.getStartDate())
                .endDate(plan.getEndDate())
                .goal(plan.getGoal())
                .status(plan.getStatus())
                .sessions(sessionDTOs)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTrainingSessionToPlan(Integer planId, TrainingSessionCreateRequestDTO request) {
        TrainingPlan plan = trainingPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Giáo án với ID: " + planId));

        if ("CANCELLED".equalsIgnoreCase(plan.getStatus()) || "COMPLETED".equalsIgnoreCase(plan.getStatus())) {
            throw new RuntimeException("Không thể thêm buổi tập vào giáo án đã hoàn thành hoặc bị hủy.");
        }

        User assignee = userRepository.findById(request.getAssignedToId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên được phân công."));

        TrainingSession session = TrainingSession.builder()
                .sessionDate(request.getSessionDate())
                .activityType(request.getActivityType())
                .notes(request.getNotes())
                .status("PENDING")
                .trainingPlan(plan)
                .assignedTo(assignee)
                .build();

        trainingSessionRepository.save(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer createTrainingPlan(TrainingPlanCreateRequestDTO request) {
        // Trainer đang đăng nhập là người phụ trách giáo án
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User trainer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));

        Horse horse = horseRepository.findById(request.getHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + request.getHorseId()));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Ngày kết thúc (endDate) không được nhỏ hơn ngày bắt đầu (startDate).");
        }
        // Ngựa chưa được duyệt (chưa có chuồng) thì chưa thuộc CLB quản lý
        if (horse.getStall() == null) {
            throw new IllegalArgumentException("Ngựa chưa được duyệt và cấp chuồng, chưa thể lập giáo án.");
        }
        // Ngựa đang bị khóa y tế (VetLock) thì không được huấn luyện
        if (Boolean.TRUE.equals(horse.getIsLocked())) {
            throw new IllegalArgumentException("Ngựa đang bị khóa y tế (VetLock), không thể lập giáo án huấn luyện.");
        }
        // Mỗi ngựa chỉ có tối đa một giáo án ACTIVE tại một thời điểm
        if (trainingPlanRepository.existsByHorse_HorseIdAndStatus(horse.getHorseId(), "ACTIVE")) {
            throw new IllegalArgumentException("Ngựa này đang có một giáo án ACTIVE. Hãy hoàn thành hoặc hủy giáo án cũ trước.");
        }

        TrainingPlan plan = TrainingPlan.builder()
                .planName(request.getPlanName().trim())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .goal(request.getGoal())
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .horse(horse)
                .headTrainer(trainer)
                .build();

        return trainingPlanRepository.save(plan).getPlanId();
    }
}
