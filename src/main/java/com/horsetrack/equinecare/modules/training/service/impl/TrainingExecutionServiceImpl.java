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
import com.horsetrack.equinecare.modules.training.service.TrainingPlanActivator;
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
    private final com.horsetrack.equinecare.modules.training.service.TrainingPlanActivator planActivator;
    private final com.horsetrack.equinecare.modules.horse.service.HorseAccessGuard horseAccessGuard;
    private final com.horsetrack.equinecare.modules.medical.repository.VetCaseRepository vetCaseRepository;
    private final com.horsetrack.equinecare.modules.audit.service.AuditTrail auditTrail;

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

        boolean isAssigned = session.getAssignedTo() != null && session.getAssignedTo().getUserId().equals(user.getUserId());
        boolean isTrainerOrManager = "Trainer".equalsIgnoreCase(user.getRoleName()) || "Club Manager".equalsIgnoreCase(user.getRoleName());

        if (!isAssigned && !isTrainerOrManager) {
            throw new AccessDeniedException("You are not assigned to this session");
        }

        java.util.List<String> cancellableStatuses = java.util.List.of(
                TrainingPlanActivator.SESSION_PENDING,
                TrainingPlanActivator.SESSION_CONFIRMED,
                "IN_PROGRESS",
                "PAUSED",
                "PAUSED_PENDING_TRAINER"
        );
        if ("CANCELLED".equalsIgnoreCase(request.getStatus())) {
            if (!cancellableStatuses.contains(String.valueOf(session.getStatus()).toUpperCase())) {
                throw new IllegalArgumentException("Không thể hủy buổi tập đang ở trạng thái " + session.getStatus() + ".");
            }
        } else {
            if (!TrainingPlanActivator.OPEN_SESSION_STATUSES.contains(String.valueOf(session.getStatus()).toUpperCase())) {
                throw new IllegalArgumentException("Không thể cập nhật buổi tập đang ở trạng thái " + session.getStatus() + ".");
            }
        }
        // Buổi tập phải được Groom xác nhận (PUT /{sessionId}/confirm) trước khi thực hiện
        if ("COMPLETED".equalsIgnoreCase(request.getStatus()) && TrainingPlanActivator.SESSION_PENDING.equalsIgnoreCase(session.getStatus())) {
            throw new IllegalArgumentException("Buổi tập chưa được xác nhận. Hãy xác nhận buổi tập trước khi hoàn thành.");
        }

        if ("CANCELLED".equalsIgnoreCase(request.getStatus()) && (request.getNotes() == null || request.getNotes().trim().isEmpty())) {
            throw new IllegalArgumentException("Phải ghi lý do (notes) khi hủy buổi tập.");
        }

        // F22-01 (BR_01): ngựa đang khóa huấn luyện thì không được tập / hoàn thành buổi tập (chỉ được hủy)
        if ("COMPLETED".equalsIgnoreCase(request.getStatus()) && session.getTrainingPlan() != null
                && session.getTrainingPlan().getHorse() != null && Boolean.TRUE.equals(session.getTrainingPlan().getHorse().getIsLocked())) {
            throw new IllegalArgumentException("Ngựa " + session.getTrainingPlan().getHorse().getHorseName()
                    + " đang bị khóa huấn luyện (VetLock), không thể thực hiện buổi tập. Hãy hủy buổi tập.");
        }

        if ("COMPLETED".equalsIgnoreCase(request.getStatus())) {
            // Phòng khi tác vụ định kỳ chưa kịp chạy: giáo án đủ điều kiện thì kích hoạt trước khi ghi nhận kết quả
            planActivator.tryActivate(session.getTrainingPlan(), LocalDate.now());
            TrainingPlan plan = session.getTrainingPlan();
            if (plan != null && !TrainingPlanActivator.PLAN_ACTIVE.equalsIgnoreCase(plan.getStatus())) {
                throw new IllegalArgumentException("Giáo án '" + plan.getPlanName() + "' đang ở trạng thái " + plan.getStatus()
                        + " (chưa tới ngày bắt đầu " + plan.getStartDate() + "), chưa thể hoàn thành buổi tập.");
            }
        }

        session.setStatus(request.getStatus().toUpperCase());
        session.setNotes(request.getNotes());
        trainingSessionRepository.save(session);

        if ("COMPLETED".equalsIgnoreCase(request.getStatus())) {
            TrainingPlan plan = session.getTrainingPlan();
            if (plan != null && TrainingPlanActivator.PLAN_ACTIVE.equalsIgnoreCase(plan.getStatus())) {
                long openCount = trainingSessionRepository.countByTrainingPlan_PlanIdAndStatusIn(plan.getPlanId(), TrainingPlanActivator.UNFINISHED_SESSION_STATUSES);
                if (openCount == 0) {
                    plan.setStatus("COMPLETED");
                    trainingPlanRepository.save(plan);
                }
            }
        } else if ("CANCELLED".equalsIgnoreCase(request.getStatus())) {
            TrainingPlan plan = session.getTrainingPlan();
            if (plan != null && (TrainingPlanActivator.PLAN_ACTIVE.equalsIgnoreCase(plan.getStatus()) || TrainingPlanActivator.PLAN_PENDING.equalsIgnoreCase(plan.getStatus()))) {
                plan.setStatus("PAUSED");
                trainingPlanRepository.save(plan);
            }
        }
    }

    @Override
    @Transactional
    public String stopSession(Integer sessionId, com.horsetrack.equinecare.modules.training.dto.SessionStopRequestDTO request) {
        User user = getLoggedInUser();
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new com.horsetrack.equinecare.common.exception.ResourceNotFoundException("Không tìm thấy buổi tập với ID: " + sessionId));
        TrainingPlan plan = session.getTrainingPlan();
        com.horsetrack.equinecare.modules.horse.entity.Horse horse = plan != null ? plan.getHorse() : null;

        // HLV phụ trách ngựa, Quản lý CLB hoặc Groom được giao buổi tập
        boolean isTrainer = "Trainer".equalsIgnoreCase(user.getRoleName());
        boolean isManager = "Club Manager".equalsIgnoreCase(user.getRoleName());
        boolean isAssignedGroom = session.getAssignedTo() != null && session.getAssignedTo().getUserId().equals(user.getUserId());
        if (isTrainer) {
            horseAccessGuard.checkAssigned(horse);
        } else if (!isManager && !isAssignedGroom) {
            throw new AccessDeniedException("Bạn không được phân công buổi tập này");
        }
        java.util.List<String> stoppableStatuses = java.util.List.of(
                TrainingPlanActivator.SESSION_PENDING,
                TrainingPlanActivator.SESSION_CONFIRMED,
                "IN_PROGRESS",
                "PAUSED",
                "PAUSED_PENDING_TRAINER"
        );
        if (!stoppableStatuses.contains(String.valueOf(session.getStatus()).toUpperCase())) {
            throw new IllegalArgumentException("Chỉ dừng được buổi tập chưa kết thúc (hiện tại: " + session.getStatus() + ").");
        }

        boolean requestVet = !Boolean.FALSE.equals(request.getRequestVet());
        String readings = (request.getHeartRate() != null ? "nhịp tim " + request.getHeartRate() + " bpm" : "")
                + (request.getSpeedKmh() != null ? (request.getHeartRate() != null ? ", " : "") + "tốc độ " + request.getSpeedKmh() + " km/h" : "");
        String reason = request.getReason().trim();
        String stopNote = "Dừng buổi tập (" + user.getFullName() + ")" + (readings.isEmpty() ? "" : " - " + readings) + ": " + reason;
        session.setNotes(session.getNotes() == null || session.getNotes().isBlank() ? stopNote : session.getNotes() + " | " + stopNote);

        String caseCode = null;
        if (requestVet && horse != null) {
            // Gửi ca cho bác sĩ; buổi tập chờ bác sĩ quyết định khóa hay không khóa huấn luyện
            session.setStatus(TrainingPlanActivator.SESSION_WAITING_VET);
            var vetCase = vetCaseRepository.saveAndFlush(com.horsetrack.equinecare.modules.medical.entity.VetCase.builder()
                    .horse(horse)
                    .reportedBy(user)
                    .caseType("Vượt ngưỡng trong buổi tập")
                    .description("Buổi tập #" + session.getSessionId() + " (" + session.getActivityType() + ", " + session.getSessionDate() + ")"
                            + (readings.isEmpty() ? "" : " - " + readings) + ". " + reason)
                    .severity(request.getSeverity() == null ? "HIGH" : request.getSeverity())
                    .source("SESSION_STOP")
                    .status("NEW")
                    .reportedAt(LocalDateTime.now())
                    .build());
            caseCode = vetCase.getCaseCode() != null ? vetCase.getCaseCode() : "#" + vetCase.getCaseId();
        } else {
            session.setStatus(TrainingPlanActivator.SESSION_STOPPED);
        }
        if (plan != null && (TrainingPlanActivator.PLAN_ACTIVE.equalsIgnoreCase(plan.getStatus()) || TrainingPlanActivator.PLAN_PENDING.equalsIgnoreCase(plan.getStatus()))) {
            plan.setStatus("PAUSED");
            trainingPlanRepository.save(plan);
        }
        trainingSessionRepository.save(session);
        auditTrail.record("STOP_SESSION", "training_sessions", "status=OPEN",
                "status=" + session.getStatus() + " | buổi tập #" + session.getSessionId() + (horse != null ? " | ngựa " + horse.getHorseName() : "")
                        + (caseCode != null ? " | ca " + caseCode : "") + " | " + reason);
        return session.getStatus();
    }

    @Override
    @Transactional
    public String confirmSession(Integer sessionId) {
        User user = getLoggedInUser();
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new com.horsetrack.equinecare.common.exception.ResourceNotFoundException("Không tìm thấy buổi tập với ID: " + sessionId));

        if (session.getAssignedTo() == null || !session.getAssignedTo().getUserId().equals(user.getUserId())) {
            throw new AccessDeniedException("Bạn không được phân công buổi tập này");
        }
        if (!TrainingPlanActivator.SESSION_PENDING.equalsIgnoreCase(session.getStatus())) {
            throw new IllegalArgumentException("Chỉ xác nhận được buổi tập đang chờ xác nhận (hiện tại: " + session.getStatus() + ").");
        }
        if (session.getSessionDate() != null && session.getSessionDate().toLocalDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Buổi tập ngày " + session.getSessionDate().toLocalDate() + " đã qua ngày, không thể xác nhận. Hãy báo HLV xếp lại lịch.");
        }
        TrainingPlan plan = session.getTrainingPlan();
        if (plan != null && plan.getHorse() != null && Boolean.TRUE.equals(plan.getHorse().getIsLocked())) {
            throw new IllegalArgumentException("Ngựa " + plan.getHorse().getHorseName() + " đang bị khóa huấn luyện (VetLock), không thể xác nhận buổi tập.");
        }
        if (plan != null && ("CANCELLED".equalsIgnoreCase(plan.getStatus()) || "COMPLETED".equalsIgnoreCase(plan.getStatus()))) {
            throw new IllegalArgumentException("Giáo án đã " + plan.getStatus() + ", không thể xác nhận buổi tập.");
        }

        session.setStatus(TrainingPlanActivator.SESSION_CONFIRMED);
        trainingSessionRepository.saveAndFlush(session);

        // Đã có Groom xác nhận: giáo án chạy ngay nếu đã tới ngày bắt đầu, nếu chưa thì tác vụ định kỳ sẽ kích hoạt đúng ngày
        planActivator.tryActivate(plan, LocalDate.now());
        return plan != null ? plan.getStatus() : null;
    }

    @Override
    @Transactional
    public void evaluateSession(Integer sessionId, com.horsetrack.equinecare.modules.training.dto.SessionEvaluateRequestDTO request) {
        User user = getLoggedInUser();
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new com.horsetrack.equinecare.common.exception.ResourceNotFoundException("Không tìm thấy buổi tập với ID: " + sessionId));

        if (!"COMPLETED".equalsIgnoreCase(session.getStatus())) {
            throw new IllegalArgumentException("Chỉ đánh giá được phiên đã hoàn thành.");
        }

        String nowStr = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
        String updatedNotes = com.horsetrack.equinecare.modules.training.service.SessionEvaluationHelper.attachEvaluation(
                session.getNotes(),
                request.getScore(),
                request.getComment(),
                user.getFullName() != null ? user.getFullName() : user.getUsername(),
                nowStr
        );

        session.setNotes(updatedNotes);
        trainingSessionRepository.save(session);
    }
}
