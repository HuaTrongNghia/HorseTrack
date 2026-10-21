package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import com.horsetrack.equinecare.modules.training.repository.TrainingPlanRepository;
import com.horsetrack.equinecare.modules.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Buổi tập dừng giữa chừng vì chỉ số sức khỏe vượt ngưỡng và HLV đã gửi ca cho bác sĩ (trạng thái WAITING_VET).
 * Khi bác sĩ ra quyết định - khóa huấn luyện hoặc đóng ca không khóa - buổi tập chuyển STOPPED (đã kết thúc) và
 * giáo án của buổi đó kết thúc (các buổi còn lại bị hủy), để ngựa có thể được lập giáo án mới cho lần sau
 * (nếu bị khóa thì sau khi bác sĩ mở khóa).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionIncidentHandler {

    private final TrainingSessionRepository sessionRepository;
    private final TrainingPlanRepository planRepository;
    private final EmailService emailService;

    /** Trả về số buổi tập đang chờ bác sĩ đã được kết thúc */
    @Transactional
    public int resolveWaitingVet(Horse horse, boolean vetLocked, String decisionNote) {
        List<TrainingSession> waiting = sessionRepository.findByTrainingPlan_Horse_HorseIdAndStatus(horse.getHorseId(), TrainingPlanActivator.SESSION_WAITING_VET);
        if (waiting.isEmpty()) {
            return 0;
        }
        String decision = vetLocked ? "Bác sĩ khóa huấn luyện" : "Bác sĩ kết luận không cần khóa huấn luyện";
        String detail = StringUtils.hasText(decisionNote) ? ": " + decisionNote.trim() : "";

        Map<Integer, TrainingPlan> plans = new LinkedHashMap<>();
        for (TrainingSession s : waiting) {
            s.setStatus(TrainingPlanActivator.SESSION_STOPPED);
            s.setNotes(truncate(append(s.getNotes(), "Kết thúc sau khi bác sĩ xử lý ca. " + decision + detail)));
            if (s.getTrainingPlan() != null) {
                plans.putIfAbsent(s.getTrainingPlan().getPlanId(), s.getTrainingPlan());
            }
        }
        sessionRepository.saveAll(waiting);

        for (TrainingPlan plan : plans.values()) {
            // Các buổi chưa thực hiện của giáo án bị hủy; giáo án kết thúc để có thể lập giáo án mới
            List<TrainingSession> rest = plan.getSessions() == null ? List.of() : plan.getSessions().stream()
                    .filter(x -> TrainingPlanActivator.OPEN_SESSION_STATUSES.contains(String.valueOf(x.getStatus()).toUpperCase()))
                    .toList();
            for (TrainingSession x : rest) {
                x.setStatus("CANCELLED");
                x.setNotes(truncate(append(x.getNotes(), "Hủy do giáo án kết thúc sau sự cố sức khỏe")));
            }
            sessionRepository.saveAll(rest);

            boolean anyCompleted = plan.getSessions() != null && plan.getSessions().stream().anyMatch(x -> "COMPLETED".equalsIgnoreCase(x.getStatus()));
            if (!"COMPLETED".equalsIgnoreCase(plan.getStatus()) && !"CANCELLED".equalsIgnoreCase(plan.getStatus())) {
                plan.setStatus(anyCompleted ? "COMPLETED" : "CANCELLED");
                plan.setPausedReason(null);
                planRepository.save(plan);
            }
            notifyTrainer(plan, horse, decision + detail, rest.size());
        }
        log.info("Ngựa {}: kết thúc {} buổi tập chờ bác sĩ, đóng {} giáo án ({})", horse.getHorseId(), waiting.size(), plans.size(), decision);
        return waiting.size();
    }

    private void notifyTrainer(TrainingPlan plan, Horse horse, String decision, int cancelled) {
        if (plan.getHeadTrainer() == null || !StringUtils.hasText(plan.getHeadTrainer().getEmail())) {
            return;
        }
        try {
            emailService.sendAsync(plan.getHeadTrainer().getEmail(), "[HorseTrack] Bác sĩ đã xử lý sự cố của ngựa " + horse.getHorseName(),
                    "<p>Xin chào <b>" + HtmlUtils.htmlEscape(plan.getHeadTrainer().getFullName()) + "</b>,</p>"
                            + "<p>" + HtmlUtils.htmlEscape(decision) + ".</p>"
                            + "<p>Buổi tập bị dừng đã kết thúc; giáo án <b>" + HtmlUtils.htmlEscape(plan.getPlanName()) + "</b> chuyển <b>"
                            + plan.getStatus() + "</b>" + (cancelled > 0 ? " và " + cancelled + " buổi tập còn lại đã bị hủy" : "") + ".</p>"
                            + "<p>Bạn có thể lập giáo án mới cho ngựa " + HtmlUtils.htmlEscape(horse.getHorseName())
                            + (Boolean.TRUE.equals(horse.getIsLocked()) ? " sau khi bác sĩ mở khóa huấn luyện." : ".") + "</p>");
        } catch (Exception ex) {
            log.warn("Không gửi được email kết thúc sự cố tới HLV: {}", ex.getMessage());
        }
    }

    private static String append(String notes, String add) {
        return StringUtils.hasText(notes) ? notes + " | " + add : add;
    }

    private static String truncate(String s) {
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }
}
