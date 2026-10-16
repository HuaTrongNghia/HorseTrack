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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final UserRepository userRepository; // ĐÃ THÊM USER REPO

    @Override
    public List<AvailableHorseDTO> getAvailableHorses() { /* Code cũ giữ nguyên */ return null; }

    @Override
    public DashboardStatsDTO getDashboardStats() { /* Code cũ giữ nguyên */ return null; }

    @Override
    public Page<TrainingPlanSummaryDTO> getTrainingPlans(String status, Pageable pageable) { /* Code cũ giữ nguyên */ return null; }

    // ==========================================
    // CÁC HÀM MỚI PHỤC VỤ CHO MÀN HÌNH LẬP LỊCH
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public List<StaffAssignDTO> getAssignableStaff() {
        // Lấy danh sách những người có thể được gán việc dắt ngựa/huấn luyện
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

        // Map danh sách buổi tập sang DTO để Frontend rải lên Calendar
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
                .horseId(plan.getHorse().getHorseId())
                .horseName(plan.getHorse().getHorseName())
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

        if ("CANCELLED".equals(plan.getStatus()) || "COMPLETED".equals(plan.getStatus())) {
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

}