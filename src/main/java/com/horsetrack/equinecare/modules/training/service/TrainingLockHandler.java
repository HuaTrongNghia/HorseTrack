package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import com.horsetrack.equinecare.modules.training.repository.TrainingPlanRepository;
import com.horsetrack.equinecare.modules.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tác động của khóa huấn luyện (VetLock) lên giáo án (BR_01 / F3-03, F21-02):
 * - Khi khóa: hủy mọi buổi tập chưa thực hiện (chờ xác nhận / đã xác nhận), tạm dừng giáo án ACTIVE (PAUSED),
 *   gửi email báo HLV phụ trách và chủ ngựa. Giáo án PENDING giữ nguyên trạng thái chờ.
 * - Khi mở khóa: giáo án tạm dừng quay về PENDING (các buổi tập cũ đã bị hủy), HLV phân công lại buổi tập
 *   và giáo án chạy lại khi Groom xác nhận (xem TrainingPlanActivator). Gửi email báo HLV và chủ ngựa.
 * Chạy trong transaction của nghiệp vụ khóa / mở khóa. Email gửi nền, lỗi email không làm hỏng nghiệp vụ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TrainingLockHandler {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TrainingSessionRepository sessionRepository;
    private final TrainingPlanRepository planRepository;
    private final EmailService emailService;
    private final SessionIncidentHandler incidentHandler;
    private final com.horsetrack.equinecare.modules.nutrition.service.NutritionLockHandler nutritionLockHandler;

    /** Gọi ngay sau khi ngựa chuyển sang khóa huấn luyện. Trả về số buổi tập đã hủy. */
    public int onHorseLocked(Horse horse, String reason) {
        String why = StringUtils.hasText(reason) ? reason.trim() : "Khóa huấn luyện theo chỉ định y tế";
        // Buổi tập đang chờ bác sĩ (dừng vì vượt ngưỡng) kết thúc và giáo án của nó đóng lại trước
        incidentHandler.resolveWaitingVet(horse, true, why);
        // BR_03: khẩu phần của ngựa bị khóa do bác sĩ thú y đảm nhiệm
        nutritionLockHandler.onHorseLocked(horse);

        List<TrainingSession> pending = sessionRepository.findPendingByHorse(horse.getHorseId());
        for (TrainingSession s : pending) {
            s.setStatus("CANCELLED");
            s.setNotes(truncate("Hủy tự động do ngựa bị khóa huấn luyện (VetLock): " + why, 1000));
        }
        sessionRepository.saveAll(pending);

        List<TrainingPlan> plans = planRepository.findByHorse_HorseIdAndStatus(horse.getHorseId(), "ACTIVE");
        for (TrainingPlan p : plans) {
            p.setStatus("PAUSED");
            p.setPausedReason(truncate("VetLock: " + why, 500));
        }
        planRepository.saveAll(plans);

        String sessionsHtml = pending.isEmpty() ? "<p>Không có buổi tập nào chưa thực hiện.</p>"
                : "<ul>" + pending.stream()
                    .map(s -> "<li>" + esc(s.getSessionDate().format(TIME)) + " · " + esc(s.getActivityType()) + "</li>")
                    .collect(Collectors.joining()) + "</ul>";
        String body = """
                <p>Ngựa <b>%s</b> vừa bị <b>khóa huấn luyện (VetLock)</b>.</p>
                <p>Lý do: %s</p>
                <p>Hệ thống đã hủy <b>%d</b> buổi tập chưa thực hiện và tạm dừng giáo án của ngựa:</p>
                %s
                <p>Khi Bác sĩ thú y mở khóa, HLV phân công lại buổi tập để giáo án chạy tiếp.</p>
                """.formatted(esc(horse.getHorseName()), esc(why), pending.size(), sessionsHtml);
        notify(horse, plans, "[HorseTrack] Ngựa " + horse.getHorseName() + " bị khóa huấn luyện", body);

        log.info("VetLock ngựa {}: hủy {} buổi tập, tạm dừng {} giáo án", horse.getHorseId(), pending.size(), plans.size());
        return pending.size();
    }

    /** Gọi ngay sau khi Bác sĩ thú y mở khóa huấn luyện. Trả về số giáo án được chuyển về trạng thái chờ. */
    public int onHorseUnlocked(Horse horse) {
        // BR_03: mở khóa thì khẩu phần y tế kết thúc, HLV lập lại khẩu phần thường
        nutritionLockHandler.onHorseUnlocked(horse);
        List<TrainingPlan> plans = planRepository.findByHorse_HorseIdAndStatus(horse.getHorseId(), "PAUSED");
        for (TrainingPlan p : plans) {
            p.setStatus(TrainingPlanActivator.PLAN_PENDING);
            p.setPausedReason(null);
        }
        planRepository.saveAll(plans);

        if (!plans.isEmpty()) {
            String body = """
                    <p>Ngựa <b>%s</b> đã được Bác sĩ thú y <b>mở khóa huấn luyện</b>.</p>
                    <p>Giáo án chuyển về trạng thái <b>đang chờ</b>. HLV phân công lại buổi tập; giáo án chạy lại khi Groom xác nhận buổi tập.</p>
                    """.formatted(esc(horse.getHorseName()));
            notify(horse, plans, "[HorseTrack] Ngựa " + horse.getHorseName() + " được mở khóa huấn luyện", body);
        }
        return plans.size();
    }

    // Gửi cho HLV phụ trách các giáo án và chủ ngựa (mỗi email một lần)
    private void notify(Horse horse, List<TrainingPlan> plans, String subject, String body) {
        Map<String, User> recipients = new LinkedHashMap<>();
        plans.stream().map(TrainingPlan::getHeadTrainer).filter(u -> u != null && StringUtils.hasText(u.getEmail()))
                .forEach(u -> recipients.putIfAbsent(u.getEmail().toLowerCase(), u));
        if (horse.getOwner() != null && StringUtils.hasText(horse.getOwner().getEmail())) {
            recipients.putIfAbsent(horse.getOwner().getEmail().toLowerCase(), horse.getOwner());
        }
        recipients.values().forEach(u -> {
            try {
                emailService.sendAsync(u.getEmail(), subject, "<p>Xin chào <b>" + esc(u.getFullName()) + "</b>,</p>" + body);
            } catch (Exception ex) {
                log.warn("Không gửi được thông báo VetLock tới {}: {}", u.getEmail(), ex.getMessage());
            }
        });
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
