package com.horsetrack.equinecare.modules.training.service.impl;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.GroomProfileRepository;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingPlanServiceImpl implements TrainingPlanService {

    private final TrainingPlanRepository trainingPlanRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final GroomProfileRepository groomProfileRepository;
    private final com.horsetrack.equinecare.modules.horse.service.HorseAccessGuard horseAccessGuard;
    private final com.horsetrack.equinecare.modules.training.service.SessionReadingView sessionReadingView;
    private final com.horsetrack.equinecare.modules.training.service.SessionVetLink sessionVetLink;

    // Thời lượng mặc định một buổi tập (bảng chỉ lưu giờ bắt đầu), dùng để kiểm tra trùng giờ
    @org.springframework.beans.factory.annotation.Value("${app.training.session-duration-minutes:60}")
    private long sessionDurationMinutes;

    @Override
    public List<AvailableHorseDTO> getAvailableHorses() {
        // Trainer chỉ thấy ngựa được Club Manager phân công cho mình
        Integer trainerId = horseAccessGuard.trainerScopeId();
        List<Horse> availableHorses = trainerId != null
                ? horseRepository.findAvailableHorsesForTrainer(trainerId)
                : horseRepository.findAvailableHorses();
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
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);

        // Trainer: chỉ thống kê trên các ngựa được phân công
        Integer trainerId = horseAccessGuard.trainerScopeId();
        long totalActivePlans = trainerId != null
                ? trainingPlanRepository.countByHorse_AssignedTrainer_UserIdAndStatus(trainerId, "ACTIVE")
                : trainingPlanRepository.countByStatus("ACTIVE");
        long todaysSessions = trainerId != null
                ? trainingSessionRepository.countByTrainingPlan_Horse_AssignedTrainer_UserIdAndSessionDateBetween(trainerId, startOfDay, endOfDay)
                : trainingSessionRepository.countBySessionDateBetween(startOfDay, endOfDay);
        long injuredHorses = trainerId != null
                ? horseRepository.countByAssignedTrainer_UserIdAndHealthStatus(trainerId, "Injured")
                : horseRepository.countByHealthStatus("Injured");

        return DashboardStatsDTO.builder()
                .totalActivePlans(totalActivePlans)
                .todaysSessions(todaysSessions)
                .injuredHorses(injuredHorses)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ManagerTodayOverviewDTO getManagerTodayOverview() {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        List<ManagerTodaySessionDTO> sessions = trainingSessionRepository.findTodayWithPlanHorse(dayStart, dayEnd).stream()
                .map(s -> {
                    Horse horse = s.getTrainingPlan().getHorse();
                    User trainer = horse.getAssignedTrainer();
                    return ManagerTodaySessionDTO.builder()
                            .sessionId(s.getSessionId())
                            .sessionDate(s.getSessionDate())
                            .horseName(horse.getHorseName())
                            .planName(s.getTrainingPlan().getPlanName())
                            .activityType(s.getActivityType())
                            .trainerName(trainer != null ? trainer.getFullName() : null)
                            .status(s.getStatus())
                            .notes(s.getNotes())
                            .build();
                })
                .collect(Collectors.toList());
        return ManagerTodayOverviewDTO.builder()
                .horseCount((int) horseRepository.countManaged())
                .newHorsesThisMonth((int) horseRepository.countManagedCreatedBetween(monthStart, monthEnd))
                .sessions(sessions)
                .build();
    }

    @Override
    public Page<TrainingPlanSummaryDTO> getTrainingPlans(String status, Pageable pageable) {
        Page<TrainingPlan> plansPage;
        boolean byStatus = status != null && !status.trim().isEmpty();
        // Trainer chỉ thấy giáo án của các ngựa được phân công cho mình
        Integer trainerId = horseAccessGuard.trainerScopeId();
        if (trainerId != null) {
            plansPage = byStatus
                    ? trainingPlanRepository.findByHorse_AssignedTrainer_UserIdAndStatus(trainerId, status.trim(), pageable)
                    : trainingPlanRepository.findByHorse_AssignedTrainer_UserId(trainerId, pageable);
        } else if (byStatus) {
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
                    .type(plan.getPlanType())
                    .goal(plan.getGoal())
                    .phases(com.horsetrack.equinecare.modules.training.service.TrainingPlanPhases.toDtos(plan))
                    .build();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffAssignDTO> getAssignableStaff(Integer planId) {
        TrainingPlan plan = findPlan(planId);
        horseAccessGuard.checkAssigned(plan.getHorse());
        String barnZone = horseBarnZone(plan);
        return groomProfileRepository.findActiveGroomsInZone(barnZone).stream()
                .map(gp -> StaffAssignDTO.builder()
                        .userId(gp.getUser().getUserId())
                        .fullName(gp.getUser().getFullName())
                        .roleName(gp.getUser().getRoleName())
                        .barnZone(gp.getAssignedBarnZone())
                        .build())
                .collect(Collectors.toList());
    }

    private TrainingPlan findPlan(Integer planId) {
        return trainingPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giáo án với ID: " + planId));
    }

    // Khu chuồng của ngựa trong giáo án; ngựa chưa có chuồng thì không phân công được
    private String horseBarnZone(TrainingPlan plan) {
        Horse horse = plan.getHorse();
        if (horse == null || horse.getStall() == null || horse.getStall().getBarnZone() == null) {
            throw new IllegalArgumentException("Ngựa của giáo án chưa được cấp chuồng nên chưa xác định được khu để phân công Groom.");
        }
        return horse.getStall().getBarnZone().trim();
    }

    @Override
    @Transactional(readOnly = true)
    public TrainingPlanDetailDTO getTrainingPlanDetail(Integer planId) {
        TrainingPlan plan = findPlan(planId);
        horseAccessGuard.checkAssigned(plan.getHorse());

        var readings = sessionReadingView.load(plan.getSessions());
        var caseCodes = sessionVetLink.caseCodes(plan.getSessions().stream().map(TrainingSession::getSessionId).toList());
        List<TrainingSessionDTO> sessionDTOs = plan.getSessions().stream()
                .map(s -> {
                    com.horsetrack.equinecare.modules.training.service.SessionEvaluationHelper.EvaluationData eval =
                            com.horsetrack.equinecare.modules.training.service.SessionEvaluationHelper.extractEvaluation(s.getNotes());
                    return TrainingSessionDTO.builder()
                            .sessionId(s.getSessionId())
                            .sessionDate(s.getSessionDate())
                            .activityType(s.getActivityType())
                            .status(s.getStatus())
                            .notes(com.horsetrack.equinecare.modules.training.service.SessionEvaluationHelper.removeEvaluation(s.getNotes()))
                            .assignedToId(s.getAssignedTo() != null ? s.getAssignedTo().getUserId() : null)
                            .assignedToName(s.getAssignedTo() != null ? s.getAssignedTo().getFullName() : null)
                            .evaluationScore(eval != null ? eval.getScore() : null)
                            .evaluationComment(eval != null ? eval.getComment() : null)
                            .evaluatedBy(eval != null ? eval.getBy() : null)
                            .evaluatedAt(eval != null ? eval.getAt() : null)
                            .maxHeartRate(sessionReadingView.maxHeartRate(s))
                            .maxSpeedKmh(sessionReadingView.maxSpeedKmh(s))
                            .startedAt(s.getStartedAt())
                            .pausedAt(s.getPausedAt())
                            .pausedReason(s.getPausedReason())
                            .readings(sessionReadingView.phase(readings.get(s.getSessionId()), com.horsetrack.equinecare.modules.training.entity.SessionReading.PHASE_SESSION))
                            .recovery(sessionReadingView.phase(readings.get(s.getSessionId()), com.horsetrack.equinecare.modules.training.entity.SessionReading.PHASE_RECOVERY))
                            .phaseNo(s.getSessionDate() == null ? null : com.horsetrack.equinecare.modules.training.service.TrainingPlanPhases
                                    .phaseOn(plan, s.getSessionDate().toLocalDate()).map(ph -> ph.getPhaseNo()).orElse(null))
                            .vetCaseCode(caseCodes.get(s.getSessionId()))
                            .build();
                })
                .collect(Collectors.toList());

        return TrainingPlanDetailDTO.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .horseId(plan.getHorse() != null ? plan.getHorse().getHorseId() : null)
                .horseName(plan.getHorse() != null ? plan.getHorse().getHorseName() : null)
                .startDate(plan.getStartDate())
                .endDate(plan.getEndDate())
                .goal(plan.getGoal())
                .type(plan.getPlanType())
                .status(plan.getStatus())
                .phases(com.horsetrack.equinecare.modules.training.service.TrainingPlanPhases.toDtos(plan))
                .sessions(sessionDTOs)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTrainingSessionToPlan(Integer planId, TrainingSessionCreateRequestDTO request) {
        TrainingPlan plan = findPlan(planId);
        horseAccessGuard.checkAssigned(plan.getHorse());

        if ("CANCELLED".equalsIgnoreCase(plan.getStatus()) || "COMPLETED".equalsIgnoreCase(plan.getStatus())) {
            throw new IllegalArgumentException("Không thể thêm buổi tập vào giáo án đã hoàn thành hoặc bị hủy.");
        }

        // BR-03: ngựa đang khóa huấn luyện (VetLock) thì không được xếp lịch tập
        Horse horse = plan.getHorse();
        if (horse != null && Boolean.TRUE.equals(horse.getIsLocked())) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " đang bị khóa huấn luyện (VetLock), không thể xếp lịch tập.");
        }

        // Ngày tập phải nằm trong thời gian của giáo án
        LocalDate day = request.getSessionDate().toLocalDate();
        if (day.isBefore(plan.getStartDate()) || day.isAfter(plan.getEndDate())) {
            throw new IllegalArgumentException("Ngày tập " + day + " nằm ngoài thời gian giáo án (" + plan.getStartDate() + " đến " + plan.getEndDate() + ").");
        }

        // Người được gán phải là Groom đang hoạt động, phụ trách đúng khu chuồng của ngựa
        String barnZone = horseBarnZone(plan);
        // QA H3: khóa dòng Groom để 2 buổi tập phân công song song cho cùng Groom không cùng qua kiểm tra trùng giờ
        User assignee = userRepository.findByIdForUpdate(request.getAssignedToId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên với ID: " + request.getAssignedToId()));
        if (!"Groom".equals(assignee.getRoleName())) {
            throw new IllegalArgumentException("Chỉ được phân công buổi tập cho Groom (người được chọn có vai trò " + assignee.getRoleName() + ").");
        }
        if (Boolean.FALSE.equals(assignee.getIsActive())) {
            throw new IllegalArgumentException("Tài khoản Groom " + assignee.getFullName() + " đã bị vô hiệu hóa.");
        }
        com.horsetrack.equinecare.modules.auth.service.StaffAccountService.requireConfirmed(assignee, "phân công buổi tập");
        String groomZone = groomProfileRepository.findById(assignee.getUserId())
                .map(gp -> gp.getAssignedBarnZone())
                .orElse(null);
        if (groomZone == null || !groomZone.trim().equalsIgnoreCase(barnZone)) {
            throw new IllegalArgumentException("Groom " + assignee.getFullName() + " phụ trách khu '" + (groomZone == null ? "chưa có" : groomZone)
                    + "', không phải khu '" + barnZone + "' của ngựa " + (horse != null ? horse.getHorseName() : "") + ".");
        }

        TrainingSession session = TrainingSession.builder()
                .sessionDate(request.getSessionDate())
                .activityType(request.getActivityType())
                .notes(request.getNotes())
                .maxHeartRate(request.getMaxHeartRate() != null ? request.getMaxHeartRate()
                        : com.horsetrack.equinecare.modules.training.service.TrainingPlanPhases.phaseOn(plan, day)
                                .map(ph -> ph.getHrLimitBpm()).filter(java.util.Objects::nonNull)
                                .orElse(sessionReadingView.maxHeartRate(new TrainingSession())))
                .maxSpeedKmh(request.getMaxSpeedKmh() != null ? request.getMaxSpeedKmh() : sessionReadingView.maxSpeedKmh(new TrainingSession()))
                .status("PENDING")
                .trainingPlan(plan)
                .assignedTo(assignee)
                .build();

        // F21-05 (BR34): một Groom không được có 2 buổi tập giao nhau về khung giờ
        LocalDateTime start = request.getSessionDate();
        List<TrainingSession> clashes = trainingSessionRepository.findOverlapping(assignee.getUserId(),
                start.minusMinutes(sessionDurationMinutes), start.plusMinutes(sessionDurationMinutes));
        if (!clashes.isEmpty()) {
            TrainingSession c = clashes.get(0);
            throw new IllegalArgumentException("Groom " + assignee.getFullName() + " đã có buổi tập lúc " + c.getSessionDate()
                    + " (ngựa " + (c.getTrainingPlan().getHorse() != null ? c.getTrainingPlan().getHorse().getHorseName() : "?")
                    + "), trùng khung giờ " + sessionDurationMinutes + " phút với buổi mới.");
        }

        trainingSessionRepository.save(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer createTrainingPlan(TrainingPlanCreateRequestDTO request) {
        // Trainer đang đăng nhập là người phụ trách giáo án
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User trainer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));

        // QA H3: khóa dòng ngựa để 2 yêu cầu lập giáo án song song không cùng qua kiểm tra (chỉ mục UX_plan_open_per_horse chặn thêm ở DB)
        Horse horse = horseRepository.findByIdForUpdate(request.getHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + request.getHorseId()));
        // Trainer chỉ lập giáo án cho ngựa được Club Manager phân công cho mình
        horseAccessGuard.checkAssigned(horse);

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
        // Mỗi ngựa chỉ có tối đa một giáo án chưa kết thúc (PENDING, ACTIVE hoặc PAUSED do VetLock) tại một thời điểm
        if (trainingPlanRepository.existsByHorse_HorseIdAndStatusIn(horse.getHorseId(), com.horsetrack.equinecare.modules.training.service.TrainingPlanActivator.OPEN_PLAN_STATUSES)) {
            throw new IllegalArgumentException("Ngựa này đang có một giáo án PENDING, ACTIVE hoặc PAUSED. Hãy hoàn thành hoặc hủy giáo án cũ trước.");
        }

        TrainingPlan plan = TrainingPlan.builder()
                .planName(request.getPlanName().trim())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .goal(request.getGoal())
                .planType(request.getType() != null && !request.getType().isBlank() ? request.getType().trim() : null)
                // Giáo án mới ở trạng thái chờ; chỉ chạy khi Groom xác nhận buổi tập và tới ngày bắt đầu
                .status(com.horsetrack.equinecare.modules.training.service.TrainingPlanActivator.PLAN_PENDING)
                .createdAt(LocalDateTime.now())
                .horse(horse)
                .headTrainer(trainer)
                .build();
        plan.getPhases().addAll(com.horsetrack.equinecare.modules.training.service.TrainingPlanPhases.build(plan, request.getPhases()));

        return trainingPlanRepository.save(plan).getPlanId();
    }
}
