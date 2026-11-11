package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.notification.service.AppNotificationService;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlanPhase;
import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import com.horsetrack.equinecare.modules.training.repository.TrainingPlanRepository;
import com.horsetrack.equinecare.modules.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gỡ kẹt giáo án / buổi tập thay cho sửa DB bằng SQL:
 * - Job tự đóng buổi tập quá hạn (mặc định 24 giờ sau giờ tập): chưa bắt đầu → CANCELLED, đang tập / tạm dừng → STOPPED.
 * - Quản lý CLB đóng cưỡng chế một buổi tập, hủy cưỡng chế giáo án, gán loại đường cho giáo án cũ. Mọi thao tác ghi Audit_Logs.
 * Buổi tập chờ bác sĩ (WAITING_VET) không đóng ở đây: bác sĩ xử lý ca khám trước.
 * Mỗi lần đóng / hủy báo Mã phu, HLV phụ trách và chủ ngựa (chủ ngựa trả phí chăm sóc nên cần biết lịch tập thay đổi).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingAdminService {

    private static final List<String> NOT_STARTED = List.of(TrainingPlanActivator.SESSION_PENDING, TrainingPlanActivator.SESSION_CONFIRMED);
    private static final List<String> STARTED = List.of(TrainingPlanActivator.SESSION_IN_PROGRESS, TrainingPlanActivator.SESSION_PAUSED_PENDING_TRAINER);
    private static final List<String> ALL_OPEN = List.of(TrainingPlanActivator.SESSION_PENDING, TrainingPlanActivator.SESSION_CONFIRMED,
            TrainingPlanActivator.SESSION_IN_PROGRESS, TrainingPlanActivator.SESSION_PAUSED_PENDING_TRAINER);
    private static final String GROOM_LINK = "/groom/sessions";
    private static final String TRAINER_LINK = "/trainer/plans";
    private static final String OWNER_LINK = "/owner/horses/";

    private final TrainingSessionRepository sessionRepository;
    private final TrainingPlanRepository planRepository;
    private final TrainingPlanLifecycle planLifecycle;
    private final TrackSlotGuard trackSlotGuard;
    private final AppNotificationService appNotifications;
    private final AuditTrail auditTrail;

    @Value("${app.training.stale-session-hours:24}")
    private long staleHours;

    /** Buổi tập còn đóng cưỡng chế được (chưa kết thúc và không chờ bác sĩ) */
    public static boolean canForceClose(TrainingSession s) {
        String st = String.valueOf(s.getStatus()).toUpperCase();
        return NOT_STARTED.contains(st) || STARTED.contains(st);
    }

    /** Giáo án còn mở và không có buổi chờ bác sĩ */
    public static boolean canForceCancel(TrainingPlan plan) {
        return TrainingPlanActivator.OPEN_PLAN_STATUSES.contains(String.valueOf(plan.getStatus()).toUpperCase())
                && plan.getSessions().stream().noneMatch(s -> TrainingPlanActivator.SESSION_WAITING_VET.equalsIgnoreCase(s.getStatus()));
    }

    // ---------- Job ----------

    @Scheduled(fixedDelayString = "${app.training.stale-check-ms:3600000}", initialDelayString = "${app.training.stale-initial-delay-ms:180000}")
    @Transactional
    public void closeStaleJob() {
        int n = closeStale(LocalDateTime.now());
        if (n > 0) {
            log.info("Đã tự đóng {} buổi tập quá hạn {} giờ", n, staleHours);
        }
    }

    /** Đóng mọi buổi chưa kết thúc có giờ tập trước now - staleHours. Trả về số buổi đã đóng. */
    @Transactional
    public int closeStale(LocalDateTime now) {
        List<String> statuses = new java.util.ArrayList<>(NOT_STARTED);
        statuses.addAll(STARTED);
        List<TrainingSession> stale = sessionRepository.findByStatusInAndSessionDateBefore(statuses, now.minusHours(staleHours));
        Map<Integer, TrainingPlan> plans = new LinkedHashMap<>();
        for (TrainingSession s : stale) {
            close(s, "Hệ thống tự đóng: quá " + staleHours + " giờ sau giờ tập mà buổi chưa kết thúc", "AUTO_CLOSE_STALE_SESSION");
            if (s.getTrainingPlan() != null) {
                plans.putIfAbsent(s.getTrainingPlan().getPlanId(), s.getTrainingPlan());
            }
        }
        plans.values().forEach(planLifecycle::completeIfFinished);
        return stale.size();
    }

    // ---------- Quản lý CLB ----------

    @Transactional
    public String forceCloseSession(Integer sessionId, String reason) {
        TrainingSession s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi tập với ID: " + sessionId));
        if (TrainingPlanActivator.SESSION_WAITING_VET.equalsIgnoreCase(s.getStatus())) {
            throw new IllegalArgumentException("Buổi tập #" + sessionId + " đang chờ bác sĩ xử lý ca khám. Bác sĩ đóng ca trước, không đóng cưỡng chế.");
        }
        if (!canForceClose(s)) {
            throw new IllegalArgumentException("Buổi tập #" + sessionId + " đã kết thúc (" + s.getStatus() + ").");
        }
        String newStatus = close(s, "Quản lý CLB " + actorName() + " đóng cưỡng chế: " + reason.trim(), "FORCE_CLOSE_SESSION");
        planLifecycle.completeIfFinished(s.getTrainingPlan());
        return newStatus;
    }

    @Transactional
    public String forceCancelPlan(Integer planId, String reason) {
        TrainingPlan plan = findPlan(planId);
        if (!TrainingPlanActivator.OPEN_PLAN_STATUSES.contains(String.valueOf(plan.getStatus()).toUpperCase())) {
            throw new IllegalArgumentException("Giáo án #" + planId + " đã " + plan.getStatus() + ", không cần hủy.");
        }
        if (!canForceCancel(plan)) {
            throw new IllegalArgumentException("Giáo án #" + planId + " có buổi tập đang chờ bác sĩ xử lý ca khám. Bác sĩ đóng ca trước rồi mới hủy giáo án.");
        }
        String why = "Quản lý CLB " + actorName() + " hủy cưỡng chế giáo án: " + reason.trim();
        int closed = 0;
        for (TrainingSession s : plan.getSessions()) {
            if (canForceClose(s)) {
                close(s, why, null);
                closed++;
            }
        }
        String before = plan.getStatus();
        plan.setStatus("CANCELLED");
        plan.setPausedReason(null);
        planRepository.save(plan);
        auditTrail.record("FORCE_CANCEL_TRAINING_PLAN", "Training_Plans", "status=" + before,
                "status=CANCELLED | giáo án #" + planId + horseLabel(plan) + " | đóng " + closed + " buổi tập | " + why);
        User trainer = trainerOf(plan);
        appNotifications.push(trainer, "TRAINING_PLAN_CANCELLED", "Giáo án " + plan.getPlanName() + " bị hủy",
                "Giáo án \"" + plan.getPlanName() + "\"" + horseLabel(plan) + " đã bị hủy. " + why + ". Ngựa lập được giáo án mới.", TRAINER_LINK);
        pushOwner(plan, "TRAINING_PLAN_CANCELLED", "Giáo án " + plan.getPlanName() + " bị hủy",
                "Giáo án \"" + plan.getPlanName() + "\"" + horseLabel(plan) + " đã bị hủy, " + closed + " buổi tập chưa diễn ra được hủy theo. "
                        + why + ". HLV phụ trách sẽ lập giáo án mới cho ngựa khi phù hợp.");
        return plan.getStatus();
    }

    /** Gán loại đường cho giáo án còn mở (giáo án lập trước khi có loại đường); kiểm tra trùng giờ các buổi chưa kết thúc */
    @Transactional
    public String setPlanTrack(Integer planId, String rawTrack) {
        TrainingPlan plan = findPlan(planId);
        if (!TrainingPlanActivator.OPEN_PLAN_STATUSES.contains(String.valueOf(plan.getStatus()).toUpperCase())) {
            throw new IllegalArgumentException("Chỉ gán đường cho giáo án đang mở (giáo án #" + planId + " đang " + plan.getStatus() + ").");
        }
        if (plan.getPhases().isEmpty()) {
            throw new IllegalArgumentException("Giáo án #" + planId + " chưa có giai đoạn để gán loại đường.");
        }
        String track = TrainingPlanPhases.trackCode(rawTrack);
        TrainingPlanPhase phase = plan.getPhases().get(0);
        String before = phase.getTrackType();
        phase.setTrackType(track);
        planRepository.saveAndFlush(plan);
        for (TrainingSession s : sessionRepository.findByTrainingPlan_PlanIdAndStatusIn(planId, ALL_OPEN)) {
            if (s.getSessionDate() != null) {
                trackSlotGuard.assertFree(plan, plan.getHorse(), s.getSessionDate());
            }
        }
        auditTrail.record("SET_PLAN_TRACK", "Training_Plan_Phases", "track_type=" + before,
                "track_type=" + track + " | giáo án #" + planId + horseLabel(plan));
        return track;
    }

    // ---------- Hỗ trợ ----------

    /** Đóng một buổi: chưa bắt đầu → CANCELLED, đã bắt đầu → STOPPED; báo Mã phu và HLV. Trả về trạng thái mới. */
    private String close(TrainingSession s, String why, String auditAction) {
        String before = String.valueOf(s.getStatus()).toUpperCase();
        String after = NOT_STARTED.contains(before) ? "CANCELLED" : TrainingPlanActivator.SESSION_STOPPED;
        s.setStatus(after);
        String notes = StringUtils.hasText(s.getNotes()) ? s.getNotes() + " | " + why : why;
        s.setNotes(notes.length() > 4000 ? notes.substring(0, 4000) : notes);
        sessionRepository.save(s);
        TrainingPlan plan = s.getTrainingPlan();
        String what = "Buổi tập #" + s.getSessionId() + (plan != null ? horseLabel(plan) : "") + " lúc " + s.getSessionDate()
                + " chuyển " + ("CANCELLED".equals(after) ? "Đã hủy" : "Đã dừng") + ". " + why + ".";
        if (auditAction != null) {
            auditTrail.record(auditAction, "training_sessions", "status=" + before, "status=" + after + " | " + what);
        }
        appNotifications.push(s.getAssignedTo(), "TRAINING_SESSION_CLOSED", "Buổi tập #" + s.getSessionId() + " đã đóng", what, GROOM_LINK);
        if (auditAction != null && plan != null) {
            appNotifications.push(trainerOf(plan), "TRAINING_SESSION_CLOSED", "Buổi tập #" + s.getSessionId() + " đã đóng", what, TRAINER_LINK);
            pushOwner(plan, "TRAINING_SESSION_CLOSED", "Buổi tập #" + s.getSessionId() + " đã đóng", what);
        }
        return after;
    }

    private void pushOwner(TrainingPlan plan, String purpose, String title, String message) {
        if (plan.getHorse() == null || plan.getHorse().getOwner() == null) {
            return;
        }
        appNotifications.push(plan.getHorse().getOwner(), purpose, title, message, OWNER_LINK + plan.getHorse().getHorseId());
    }

    private static User trainerOf(TrainingPlan plan) {
        if (plan.getHorse() != null && plan.getHorse().getAssignedTrainer() != null) {
            return plan.getHorse().getAssignedTrainer();
        }
        return plan.getHeadTrainer();
    }

    private static String horseLabel(TrainingPlan plan) {
        return plan.getHorse() != null ? " (ngựa " + plan.getHorse().getHorseName() + ")" : "";
    }

    private static String actorName() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "hệ thống";
    }

    private TrainingPlan findPlan(Integer planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giáo án với ID: " + planId));
    }
}
