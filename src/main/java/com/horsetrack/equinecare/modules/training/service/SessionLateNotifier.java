package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Buổi tập đã qua ngày mà nhân viên vẫn chưa xác nhận: báo quản lý và ghi chú cho chủ ngựa. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionLateNotifier {

    static final String MARK = "Đã báo trễ";

    private final TrainingSessionRepository sessionRepository;
    private final TrainingPlanRepository planRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public int notify(LocalDate today) {
        LocalDateTime start = today.atStartOfDay();
        int n = 0;
        for (TrainingSession session : sessionRepository.findByStatusAndSessionDateBefore(TrainingPlanActivator.SESSION_PENDING, start)) {
            if (session.getNotes() != null && session.getNotes().contains(MARK)) {
                continue;
            }
            TrainingPlan plan = session.getTrainingPlan();
            if (plan == null || plan.getHorse() == null) {
                continue;
            }
            String note = MARK + ": buổi " + session.getSessionDate() + " chưa được nhân viên xác nhận";
            String notes = StringUtils.hasText(session.getNotes()) ? session.getNotes() + " | " + note : note;
            session.setNotes(notes.length() > 4000 ? notes.substring(0, 4000) : notes);
            sessionRepository.save(session);
            if (TrainingPlanActivator.PLAN_PENDING.equalsIgnoreCase(plan.getStatus())
                    && (plan.getPausedReason() == null || plan.getPausedReason().startsWith("Trễ:"))) {
                String reason = "Trễ: nhân viên chưa xác nhận buổi tập " + session.getSessionDate().toLocalDate();
                plan.setPausedReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
                planRepository.save(plan);
            }
            mail(plan, session);
            n++;
        }
        return n;
    }

    private void mail(TrainingPlan plan, TrainingSession session) {
        String horse = plan.getHorse().getHorseName();
        String when = String.valueOf(session.getSessionDate());
        String body = "<p>Buổi tập <b>" + HtmlUtils.htmlEscape(session.getActivityType()) + "</b> của ngựa <b>"
                + HtmlUtils.htmlEscape(horse) + "</b> (giáo án " + HtmlUtils.htmlEscape(plan.getPlanName())
                + ") lúc " + HtmlUtils.htmlEscape(when) + " đã qua mà nhân viên chưa xác nhận và chưa bắt đầu.</p>"
                + "<p>Chủ ngựa thấy ghi chú trễ trên hồ sơ giáo án.</p>";
        List<User> managers = userRepository.findByRoleNameInAndIsActiveTrue(List.of("Club Manager"));
        if (plan.getHorse().getOwner() != null) {
            managers = new java.util.ArrayList<>(managers);
            managers.add(plan.getHorse().getOwner());
        }
        for (User user : managers) {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                continue;
            }
            try {
                emailService.send("SESSION_LATE", user.getEmail(), "[HorseTrack] Buổi tập trễ — " + horse, body);
            } catch (RuntimeException ex) {
                log.warn("Không gửi được email buổi tập trễ tới {}: {}", user.getEmail(), ex.getMessage());
            }
        }
    }
}
