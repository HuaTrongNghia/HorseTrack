package com.horsetrack.equinecare.modules.nutrition.service;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlan;
import com.horsetrack.equinecare.modules.nutrition.repository.NutritionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * BR_03 khi trạng thái khóa huấn luyện của ngựa thay đổi:
 * - Ngựa bị khóa (chấn thương): khẩu phần thường (STANDARD) của HLV kết thúc, việc cho ăn chưa làm bị hủy,
 *   bác sĩ thú y nhận thông báo để lập khẩu phần y tế.
 * - Ngựa được mở khóa: khẩu phần y tế (MEDICAL) kết thúc, HLV phụ trách nhận thông báo để lập lại khẩu phần thường.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NutritionLockHandler {

    private final NutritionPlanRepository planRepository;
    private final NutritionFeedTaskService feedTaskService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public int onHorseLocked(Horse horse) {
        int n = endPlans(horse, "STANDARD", "Ngựa bị khóa huấn luyện, khẩu phần chuyển cho bác sĩ thú y");
        if (n > 0) {
            for (User vet : userRepository.findByRoleNameInAndIsActiveTrue(List.of("Veterinarian"))) {
                notify(vet, "[HorseTrack] Cần lập khẩu phần y tế cho ngựa " + horse.getHorseName(),
                        "<p>Ngựa <b>" + HtmlUtils.htmlEscape(horse.getHorseName()) + "</b> vừa bị khóa huấn luyện. Khẩu phần thường của HLV đã kết thúc; "
                                + "theo BR_03, khẩu phần của ngựa lúc này do bác sĩ thú y lập (POST /api/v1/nutrition-plans).</p>");
            }
        }
        return n;
    }

    @Transactional
    public int onHorseUnlocked(Horse horse) {
        int n = endPlans(horse, "MEDICAL", "Ngựa được mở khóa huấn luyện, khẩu phần chuyển lại cho HLV");
        if (n > 0 && horse.getAssignedTrainer() != null) {
            notify(horse.getAssignedTrainer(), "[HorseTrack] Lập lại khẩu phần cho ngựa " + horse.getHorseName(),
                    "<p>Ngựa <b>" + HtmlUtils.htmlEscape(horse.getHorseName()) + "</b> đã được bác sĩ mở khóa huấn luyện. Khẩu phần y tế đã kết thúc; "
                            + "bạn có thể lập lại khẩu phần thường cho ngựa.</p>");
        }
        return n;
    }

    private int endPlans(Horse horse, String planType, String reason) {
        LocalDate today = LocalDate.now();
        int n = 0;
        for (NutritionPlan p : planRepository.findByHorse_HorseIdAndStatusOrderByCreatedDateDesc(horse.getHorseId(), "ACTIVE")) {
            if (!planType.equals(p.getPlanType())) {
                continue;
            }
            if (p.getStartDate().isAfter(today)) {
                p.setStatus("CANCELLED");
            } else {
                p.setStatus("ENDED");
                if (p.getEndDate().isAfter(today)) {
                    p.setEndDate(today);
                }
            }
            p.setNotes(StringUtils.hasText(p.getNotes()) ? p.getNotes() + "\n" + reason : reason);
            planRepository.save(p);
            feedTaskService.skipOpenTasks(p, reason);
            n++;
        }
        if (n > 0) {
            log.info("Ngựa {}: kết thúc {} khẩu phần {} ({})", horse.getHorseId(), n, planType, reason);
        }
        return n;
    }

    private void notify(User user, String subject, String body) {
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            return;
        }
        try {
            emailService.sendAsync("NUTRITION", user.getEmail(), subject, "<p>Xin chào <b>" + HtmlUtils.htmlEscape(user.getFullName()) + "</b>,</p>" + body);
        } catch (Exception ex) {
            log.warn("Không gửi được email khẩu phần tới {}: {}", user.getEmail(), ex.getMessage());
        }
    }
}
