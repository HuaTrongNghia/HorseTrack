package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.service.HorseAccessGuard;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import com.horsetrack.equinecare.modules.training.dto.*;
import com.horsetrack.equinecare.modules.training.entity.SessionReading;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import com.horsetrack.equinecare.modules.training.repository.SessionReadingRepository;
import com.horsetrack.equinecare.modules.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * F22-02: Groom bắt đầu buổi tập và nhập chỉ số; chỉ số vượt ngưỡng thì buổi tập tự tạm dừng
 * (PAUSED_PENDING_TRAINER), chặn nhập tiếp và báo HLV. Khi tạm dừng Groom nhập nhịp tim hồi phục;
 * HLV cho tập tiếp khi nhịp hồi phục dưới ngưỡng, hoặc dừng & gửi bác sĩ (PUT /stop).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionMonitorService {

    private final TrainingSessionRepository sessionRepository;
    private final SessionReadingRepository readingRepository;
    private final UserRepository userRepository;
    private final HorseAccessGuard horseAccessGuard;
    private final TrainingPlanActivator planActivator;
    private final SessionReadingView readingView;
    private final EmailService emailService;
    private final AuditTrail auditTrail;
    private final SessionVetLink sessionVetLink;
    private final com.horsetrack.equinecare.modules.medical.repository.VetCaseRepository vetCaseRepository;

    @Transactional
    public TrainingSessionDTO start(Integer sessionId) {
        User groom = currentUser();
        TrainingSession session = findSession(sessionId);
        checkAssignedGroom(session, groom);
        if (!TrainingPlanActivator.SESSION_CONFIRMED.equalsIgnoreCase(session.getStatus())) {
            throw new IllegalArgumentException("Chỉ bắt đầu được buổi tập đã xác nhận (hiện tại: " + session.getStatus() + ").");
        }
        if (session.getSessionDate() != null && !session.getSessionDate().toLocalDate().equals(LocalDate.now())) {
            throw new IllegalArgumentException("Buổi tập xếp ngày " + session.getSessionDate().toLocalDate() + ", chỉ bắt đầu được đúng ngày tập.");
        }
        checkNotLocked(session, "bắt đầu buổi tập");
        checkNoPendingSessionStopCase(session);
        TrainingPlan plan = session.getTrainingPlan();
        planActivator.tryActivate(plan, LocalDate.now());
        if (plan != null && !TrainingPlanActivator.PLAN_ACTIVE.equalsIgnoreCase(plan.getStatus())) {
            throw new IllegalArgumentException("Giáo án '" + plan.getPlanName() + "' đang ở trạng thái " + plan.getStatus() + ", chưa thể bắt đầu buổi tập.");
        }
        session.setStatus(TrainingPlanActivator.SESSION_IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now().withNano(0));
        sessionRepository.save(session);
        auditTrail.record("START_SESSION", "training_sessions", "status=CONFIRMED",
                "status=IN_PROGRESS | buổi tập #" + session.getSessionId() + horseLabel(session));
        return toDto(session);
    }

    @Transactional
    public TrainingSessionDTO addReading(Integer sessionId, SessionReadingRequestDTO request) {
        User groom = currentUser();
        TrainingSession session = findSession(sessionId);
        checkAssignedGroom(session, groom);
        if (TrainingPlanActivator.SESSION_PAUSED_PENDING_TRAINER.equalsIgnoreCase(session.getStatus())) {
            throw new IllegalArgumentException("Buổi tập đang tạm dừng chờ HLV quyết định, không nhập thêm chỉ số. Hãy nhập nhịp tim hồi phục.");
        }
        if (!TrainingPlanActivator.SESSION_IN_PROGRESS.equalsIgnoreCase(session.getStatus())) {
            throw new IllegalArgumentException("Chỉ nhập chỉ số khi buổi tập đang diễn ra (hiện tại: " + session.getStatus() + "). Hãy bắt đầu buổi tập trước.");
        }
        checkNotLocked(session, "nhập chỉ số");

        LocalDateTime at = parseTime(request.getTime(), session);
        readingRepository.save(SessionReading.builder()
                .session(session)
                .readingPhase(SessionReading.PHASE_SESSION)
                .recordedAt(at)
                .heartRateBpm(request.getHeartRate())
                .speedKmh(request.getSpeedKmh())
                .build());

        int maxHr = readingView.maxHeartRate(session);
        BigDecimal maxSpeed = readingView.maxSpeedKmh(session);
        List<String> over = new ArrayList<>();
        if (request.getHeartRate() > maxHr) {
            over.add("nhịp tim " + request.getHeartRate() + " bpm vượt ngưỡng " + maxHr + " bpm");
        }
        if (request.getSpeedKmh().compareTo(maxSpeed) > 0) {
            over.add("tốc độ " + plain(request.getSpeedKmh()) + " km/h vượt ngưỡng " + plain(maxSpeed) + " km/h");
        }
        if (!over.isEmpty()) {
            String reason = String.join(", ", over);
            reason = Character.toUpperCase(reason.charAt(0)) + reason.substring(1);
            session.setStatus(TrainingPlanActivator.SESSION_PAUSED_PENDING_TRAINER);
            session.setPausedAt(at);
            session.setPausedReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
            session.setNotes(append(session.getNotes(), "Tạm dừng lúc " + at.toLocalTime().withNano(0) + ": " + reason));
            sessionRepository.save(session);
            auditTrail.record("PAUSE_SESSION", "training_sessions", "status=IN_PROGRESS",
                    "status=PAUSED_PENDING_TRAINER | buổi tập #" + session.getSessionId() + horseLabel(session) + " | " + reason);
            notifyTrainer(session, reason);
        }
        return toDto(session);
    }

    @Transactional
    public TrainingSessionDTO addRecovery(Integer sessionId, SessionRecoveryRequestDTO request) {
        User groom = currentUser();
        TrainingSession session = findSession(sessionId);
        checkAssignedGroom(session, groom);
        if (!TrainingPlanActivator.SESSION_PAUSED_PENDING_TRAINER.equalsIgnoreCase(session.getStatus())) {
            throw new IllegalArgumentException("Chỉ nhập nhịp tim hồi phục khi buổi tập đang tạm dừng chờ HLV (hiện tại: " + session.getStatus() + ").");
        }
        readingRepository.save(SessionReading.builder()
                .session(session)
                .readingPhase(SessionReading.PHASE_RECOVERY)
                .recordedAt(parseTime(request.getTime(), session))
                .heartRateBpm(request.getHeartRate())
                .speedKmh(BigDecimal.ZERO)
                .build());
        return toDto(session);
    }

    @Transactional
    public TrainingSessionDTO resume(Integer sessionId, SessionResumeRequestDTO request) {
        User user = currentUser();
        TrainingSession session = findSession(sessionId);
        Horse horse = session.getTrainingPlan() != null ? session.getTrainingPlan().getHorse() : null;
        if (!"Trainer".equalsIgnoreCase(user.getRoleName())) {
            throw new AccessDeniedException("Chỉ HLV phụ trách ngựa mới được cho buổi tập tiếp tục");
        }
        horseAccessGuard.checkAssigned(horse);
        if (!TrainingPlanActivator.SESSION_PAUSED_PENDING_TRAINER.equalsIgnoreCase(session.getStatus())) {
            throw new IllegalArgumentException("Chỉ cho tập tiếp buổi tập đang tạm dừng chờ HLV (hiện tại: " + session.getStatus() + ").");
        }
        checkNotLocked(session, "tiếp tục buổi tập");

        // So với ngưỡng đang áp dụng lúc tạm dừng (không dùng ngưỡng mới để tránh nới ngưỡng cho qua)
        int maxHr = readingView.maxHeartRate(session);
        SessionReading recovery = readingRepository
                .findFirstBySession_SessionIdAndReadingPhaseOrderByRecordedAtDescReadingIdDesc(session.getSessionId(), SessionReading.PHASE_RECOVERY)
                .orElseThrow(() -> new IllegalArgumentException("Chưa có nhịp tim hồi phục. Groom cần nhập nhịp hồi phục trước khi HLV cho tập tiếp."));
        if (recovery.getHeartRateBpm() >= maxHr) {
            throw new IllegalArgumentException("Nhịp tim hồi phục gần nhất " + recovery.getHeartRateBpm() + " bpm chưa dưới ngưỡng " + maxHr
                    + " bpm. Chờ ngựa hồi phục hoặc dừng buổi tập & gửi bác sĩ.");
        }
        if (request.getMaxHeartRate() != null && request.getMaxHeartRate() > maxHr) {
            throw new IllegalArgumentException("Ngưỡng nhịp tim mới không được cao hơn ngưỡng lúc tạm dừng (" + maxHr + " bpm).");
        }
        if (!StringUtils.hasText(request.getNote())) {
            throw new IllegalArgumentException("Phải ghi nội dung điều chỉnh.");
        }
        applyPhaseAdjustment(session, request);

        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(request.getIntensity())) parts.add("cường độ " + request.getIntensity().trim());
        if (request.getDistanceM() != null) parts.add("cự ly " + request.getDistanceM() + " m");
        if (request.getMaxHeartRate() != null) {
            parts.add("ngưỡng nhịp tim " + maxHr + " -> " + request.getMaxHeartRate() + " bpm");
            session.setMaxHeartRate(request.getMaxHeartRate());
        }
        StringBuilder adjust = new StringBuilder("HLV " + user.getFullName() + " cho tập tiếp");
        if (!parts.isEmpty()) adjust.append(" (").append(String.join(", ", parts)).append(")");
        if (StringUtils.hasText(request.getNote())) adjust.append(": ").append(request.getNote().trim());

        String pausedReason = session.getPausedReason();
        session.setStatus(TrainingPlanActivator.SESSION_IN_PROGRESS);
        session.setPausedAt(null);
        session.setPausedReason(null);
        session.setNotes(append(session.getNotes(), adjust.toString()));
        sessionRepository.save(session);
        auditTrail.record("RESUME_SESSION", "training_sessions", "status=PAUSED_PENDING_TRAINER | " + pausedReason,
                "status=IN_PROGRESS | buổi tập #" + session.getSessionId() + horseLabel(session) + " | hồi phục " + recovery.getHeartRateBpm() + " bpm");
        return toDto(session);
    }

    public TrainingSessionDTO toDto(TrainingSession s) {
        List<SessionReading> list = readingView.load(List.of(s)).get(s.getSessionId());
        return TrainingSessionDTO.builder()
                .sessionId(s.getSessionId())
                .sessionDate(s.getSessionDate())
                .activityType(s.getActivityType())
                .status(s.getStatus())
                .notes(SessionEvaluationHelper.removeEvaluation(s.getNotes()))
                .assignedToId(s.getAssignedTo() != null ? s.getAssignedTo().getUserId() : null)
                .assignedToName(s.getAssignedTo() != null ? s.getAssignedTo().getFullName() : null)
                .maxHeartRate(readingView.maxHeartRate(s))
                .maxSpeedKmh(readingView.maxSpeedKmh(s))
                .startedAt(s.getStartedAt())
                .pausedAt(s.getPausedAt())
                .pausedReason(s.getPausedReason())
                .readings(readingView.phase(list, SessionReading.PHASE_SESSION))
                .recovery(readingView.phase(list, SessionReading.PHASE_RECOVERY))
                .vetCaseCode(sessionVetLink.caseCodes(List.of(s.getSessionId())).get(s.getSessionId()))
                .build();
    }

    private static void applyPhaseAdjustment(TrainingSession session, SessionResumeRequestDTO request) {
        TrainingPlan plan = session.getTrainingPlan();
        if (plan == null || session.getSessionDate() == null) {
            return;
        }
        TrainingPlanPhases.phaseOn(plan, session.getSessionDate().toLocalDate()).ifPresent(ph -> {
            String intensity = TrainingPlanPhases.intensityOf(request.getIntensity());
            if (intensity != null) {
                ph.setIntensity(intensity);
            }
            if (request.getDistanceM() != null) {
                ph.setDistanceM(BigDecimal.valueOf(request.getDistanceM()));
            }
            if (request.getMaxHeartRate() != null) {
                ph.setHrLimitBpm(request.getMaxHeartRate());
            }
        });
    }

    // "HH:mm" / "HH:mm:ss" theo ngày của buổi tập, hoặc ISO date-time; bỏ trống = bây giờ
    private static LocalDateTime parseTime(String time, TrainingSession session) {
        if (!StringUtils.hasText(time)) {
            return LocalDateTime.now().withNano(0);
        }
        String t = time.trim();
        try {
            if (t.contains("T")) {
                return LocalDateTime.parse(t.length() > 19 ? t.substring(0, 19) : t);
            }
            LocalDate day = session.getSessionDate() != null ? session.getSessionDate().toLocalDate() : LocalDate.now();
            return day.atTime(LocalTime.parse(t.length() == 5 ? t + ":00" : t));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Giờ đo '" + time + "' không hợp lệ (dạng HH:mm hoặc yyyy-MM-ddTHH:mm).");
        }
    }

    private void notifyTrainer(TrainingSession session, String reason) {
        TrainingPlan plan = session.getTrainingPlan();
        Horse horse = plan != null ? plan.getHorse() : null;
        User trainer = horse != null && horse.getAssignedTrainer() != null ? horse.getAssignedTrainer()
                : plan != null ? plan.getHeadTrainer() : null;
        if (trainer == null || !StringUtils.hasText(trainer.getEmail())) {
            log.warn("Buổi tập #{} tạm dừng nhưng không có email HLV để báo", session.getSessionId());
            return;
        }
        String horseName = horse != null ? horse.getHorseName() : "?";
        try {
            emailService.sendAsync("TRAINING_PAUSE", trainer.getEmail(), "[HorseTrack] Buổi tập của ngựa " + horseName + " tạm dừng do vượt ngưỡng",
                    "<p>Xin chào <b>" + HtmlUtils.htmlEscape(String.valueOf(trainer.getFullName())) + "</b>,</p>"
                            + "<p>Buổi tập #" + session.getSessionId() + " (" + HtmlUtils.htmlEscape(session.getActivityType()) + ") của ngựa <b>"
                            + HtmlUtils.htmlEscape(horseName) + "</b> đã tự tạm dừng: " + HtmlUtils.htmlEscape(reason) + ".</p>"
                            + "<p>Groom sẽ nhập nhịp tim hồi phục. Hãy vào trang phiên tập để cho tập tiếp (khi nhịp hồi phục dưới ngưỡng) "
                            + "hoặc dừng buổi tập &amp; gửi bác sĩ.</p>");
        } catch (Exception ex) {
            log.warn("Không gửi được email tạm dừng buổi tập tới HLV: {}", ex.getMessage());
        }
    }

    private static void checkAssignedGroom(TrainingSession session, User user) {
        if (session.getAssignedTo() == null || !session.getAssignedTo().getUserId().equals(user.getUserId())) {
            throw new AccessDeniedException("Bạn không được phân công buổi tập này");
        }
    }

    // Buổi tập trước bị dừng và gửi bác sĩ: chờ bác sĩ đóng ca (hoặc khóa huấn luyện) rồi mới tập tiếp
    private void checkNoPendingSessionStopCase(TrainingSession session) {
        Horse horse = session.getTrainingPlan() != null ? session.getTrainingPlan().getHorse() : null;
        if (horse == null) {
            return;
        }
        vetCaseRepository.findFirstByHorse_HorseIdAndSourceAndStatusNotInOrderByCaseIdDesc(horse.getHorseId(), "SESSION_STOP", List.of("CLOSED", "CANCELLED"))
                .ifPresent(c -> {
                    throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " đang chờ bác sĩ khám (ca "
                            + (c.getCaseCode() != null ? c.getCaseCode() : "#" + c.getCaseId()) + ") sau buổi tập bị dừng, chưa thể bắt đầu buổi tập mới.");
                });
    }

    private static void checkNotLocked(TrainingSession session, String action) {
        Horse horse = session.getTrainingPlan() != null ? session.getTrainingPlan().getHorse() : null;
        if (horse != null && Boolean.TRUE.equals(horse.getIsLocked())) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " đang bị khóa huấn luyện (VetLock), không thể " + action + ".");
        }
    }

    private static String horseLabel(TrainingSession s) {
        return s.getTrainingPlan() != null && s.getTrainingPlan().getHorse() != null ? " | ngựa " + s.getTrainingPlan().getHorse().getHorseName() : "";
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static String append(String notes, String add) {
        String v = StringUtils.hasText(notes) ? notes + " | " + add : add;
        return v.length() > 4000 ? v.substring(0, 4000) : v;
    }

    private TrainingSession findSession(Integer sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi tập với ID: " + sessionId));
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }
}
