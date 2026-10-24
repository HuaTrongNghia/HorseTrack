package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.service.HorseAccessGuard;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import com.horsetrack.equinecare.modules.training.repository.TrainingPlanRepository;
import com.horsetrack.equinecare.modules.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * Lối thoát của giáo án (QA C1): hủy một buổi tập không tạm dừng cả giáo án; chỉ VetLock hoặc buổi tập chờ bác sĩ mới làm giáo án PAUSED.
 * HLV phụ trách / Club Manager hủy giáo án (PENDING, ACTIVE, PAUSED -> CANCELLED) hoặc cho giáo án PAUSED chạy lại.
 */
@Component
@RequiredArgsConstructor
public class TrainingPlanLifecycle {

    /** Buổi tập đang diễn ra hoặc chờ quyết định: phải xử lý xong trước khi hủy / chạy lại giáo án */
    private static final List<String> BUSY_SESSION_STATUSES = List.of(
            TrainingPlanActivator.SESSION_IN_PROGRESS, TrainingPlanActivator.SESSION_PAUSED_PENDING_TRAINER, TrainingPlanActivator.SESSION_WAITING_VET);

    private final TrainingPlanRepository planRepository;
    private final TrainingSessionRepository sessionRepository;
    private final TrainingPlanActivator planActivator;
    private final HorseAccessGuard horseAccessGuard;
    private final AuditTrail auditTrail;

    /** Giáo án ACTIVE không còn buổi chưa kết thúc và đã có buổi hoàn thành thì tự COMPLETED; chưa có buổi hoàn thành thì giữ ACTIVE để HLV thêm buổi hoặc hủy giáo án. */
    public void completeIfFinished(TrainingPlan plan) {
        if (plan == null || !TrainingPlanActivator.PLAN_ACTIVE.equalsIgnoreCase(plan.getStatus())) {
            return;
        }
        if (sessionRepository.countByTrainingPlan_PlanIdAndStatusIn(plan.getPlanId(), TrainingPlanActivator.UNFINISHED_SESSION_STATUSES) == 0
                && sessionRepository.countByTrainingPlan_PlanIdAndStatus(plan.getPlanId(), "COMPLETED") > 0) {
            plan.setStatus("COMPLETED");
            planRepository.save(plan);
        }
    }

    @Transactional
    public String cancel(Integer planId, String reason) {
        TrainingPlan plan = findPlan(planId);
        horseAccessGuard.checkAssigned(plan.getHorse());
        if (!TrainingPlanActivator.OPEN_PLAN_STATUSES.contains(String.valueOf(plan.getStatus()).toUpperCase())) {
            throw new IllegalArgumentException("Chỉ hủy được giáo án đang chờ, đang chạy hoặc tạm dừng (hiện tại: " + plan.getStatus() + ").");
        }
        checkNoBusySession(plan, "hủy");

        String why = reason.trim();
        List<TrainingSession> open = plan.getSessions().stream()
                .filter(s -> TrainingPlanActivator.OPEN_SESSION_STATUSES.contains(String.valueOf(s.getStatus()).toUpperCase()))
                .toList();
        for (TrainingSession s : open) {
            s.setStatus("CANCELLED");
            String note = "Hủy do giáo án bị hủy: " + why;
            String notes = StringUtils.hasText(s.getNotes()) ? s.getNotes() + " | " + note : note;
            s.setNotes(notes.length() > 4000 ? notes.substring(0, 4000) : notes);
        }
        sessionRepository.saveAll(open);

        String before = plan.getStatus();
        plan.setStatus("CANCELLED");
        plan.setPausedReason(null);
        planRepository.save(plan);
        auditTrail.record("CANCEL_TRAINING_PLAN", "Training_Plans", "status=" + before,
                "status=CANCELLED | giáo án #" + plan.getPlanId() + horseLabel(plan) + " | hủy " + open.size() + " buổi tập | " + why);
        return plan.getStatus();
    }

    /** Giáo án PAUSED (ngựa không còn bị khóa) chạy lại: PENDING, rồi ACTIVE ngay nếu đã tới ngày bắt đầu và có buổi đã xác nhận. */
    @Transactional
    public String resume(Integer planId) {
        TrainingPlan plan = findPlan(planId);
        Horse horse = plan.getHorse();
        horseAccessGuard.checkAssigned(horse);
        if (!"PAUSED".equalsIgnoreCase(plan.getStatus())) {
            throw new IllegalArgumentException("Chỉ cho chạy lại giáo án đang tạm dừng (hiện tại: " + plan.getStatus() + ").");
        }
        if (horse != null && Boolean.TRUE.equals(horse.getIsLocked())) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " đang bị khóa huấn luyện (VetLock). Giáo án chạy lại khi bác sĩ mở khóa.");
        }
        if (plan.getEndDate() != null && plan.getEndDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Giáo án đã quá ngày kết thúc " + plan.getEndDate() + ". Hãy hủy giáo án và lập giáo án mới.");
        }
        checkNoBusySession(plan, "chạy lại");

        String before = plan.getPausedReason();
        plan.setStatus(TrainingPlanActivator.PLAN_PENDING);
        plan.setPausedReason(null);
        planRepository.save(plan);
        planActivator.tryActivate(plan, LocalDate.now());
        auditTrail.record("RESUME_TRAINING_PLAN", "Training_Plans", "status=PAUSED" + (before != null ? " | " + before : ""),
                "status=" + plan.getStatus() + " | giáo án #" + plan.getPlanId() + horseLabel(plan));
        return plan.getStatus();
    }

    private void checkNoBusySession(TrainingPlan plan, String action) {
        plan.getSessions().stream()
                .filter(s -> BUSY_SESSION_STATUSES.contains(String.valueOf(s.getStatus()).toUpperCase()))
                .findFirst()
                .ifPresent(s -> {
                    throw new IllegalArgumentException("Không thể " + action + " giáo án: buổi tập #" + s.getSessionId() + " đang " + s.getStatus()
                            + (TrainingPlanActivator.SESSION_WAITING_VET.equalsIgnoreCase(s.getStatus())
                                ? " (chờ bác sĩ xử lý ca)." : ". Hãy hoàn thành, cho tập tiếp hoặc dừng buổi tập trước."));
                });
    }

    private static String horseLabel(TrainingPlan plan) {
        return plan.getHorse() != null ? " | ngựa " + plan.getHorse().getHorseName() : "";
    }

    private TrainingPlan findPlan(Integer planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giáo án với ID: " + planId));
    }
}
