package com.horsetrack.equinecare.modules.care.service;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.care.entity.CareTask;
import com.horsetrack.equinecare.modules.care.repository.CareTaskRepository;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SLA Monitor (BR_05 / F4-04): định kỳ tìm việc chăm sóc quá app.care.sla-minutes (mặc định 45 phút)
 * sau giờ hẹn mà Groom chưa đánh dấu hoàn thành, ghi cảnh báo vào Daily_Tasks.warning_note
 * và gửi một email tổng hợp tới các Club Manager. Mỗi việc chỉ bị cảnh báo một lần.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CareTaskSlaMonitor {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final List<String> OPEN_STATUSES = List.of("TODO", "IN_PROGRESS");

    private final CareTaskRepository careTaskRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.care.sla-minutes:45}")
    private long slaMinutes;

    @Scheduled(fixedDelayString = "${app.care.sla-check-ms:300000}", initialDelayString = "${app.care.sla-initial-delay-ms:60000}")
    @Transactional
    public void checkOverdueTasks() {
        int count = flagOverdue(LocalDateTime.now());
        if (count > 0) {
            log.warn("SLA Monitor: {} việc chăm sóc trễ quá {} phút", count, slaMinutes);
        }
    }

    /** Đánh dấu và cảnh báo các việc trễ tại thời điểm now. Trả về số việc mới bị cảnh báo. */
    @Transactional
    public int flagOverdue(LocalDateTime now) {
        List<CareTask> late = careTaskRepository
                .findByStatusInAndWarningNoteIsNullAndTaskDateLessThanEqual(OPEN_STATUSES, now.toLocalDate()).stream()
                .filter(t -> t.getTaskDate() != null && t.getScheduledTime() != null)
                .filter(t -> t.getTaskDate().atTime(t.getScheduledTime()).plusMinutes(slaMinutes).isBefore(now))
                .collect(Collectors.toList());
        if (late.isEmpty()) {
            return 0;
        }

        String note = "Trễ quá " + slaMinutes + " phút so với giờ hẹn (cảnh báo lúc " + now.format(TIME) + ")";
        late.forEach(t -> t.setWarningNote(note));
        careTaskRepository.saveAll(late);

        String rows = late.stream().map(t -> "<li><b>" + esc(t.getTaskCode()) + "</b> · " + esc(t.getTaskType())
                + " · ngựa " + esc(t.getHorse() != null ? t.getHorse().getHorseName() : "?")
                + " · Groom " + esc(t.getGroom() != null ? t.getGroom().getFullName() : "?")
                + " · hẹn " + esc(t.getTaskDate().atTime(t.getScheduledTime()).format(TIME)) + "</li>")
                .collect(Collectors.joining());
        String body = "<p>Có <b>" + late.size() + "</b> việc chăm sóc chưa hoàn thành sau " + slaMinutes
                + " phút kể từ giờ hẹn:</p><ul>" + rows + "</ul>"
                + "<p>Xem chi tiết tại GET /api/v1/care-tasks/overdue.</p>";

        List<User> managers = userRepository.findByRoleNameInAndIsActiveTrue(List.of("Club Manager"));
        for (User m : managers) {
            if (!StringUtils.hasText(m.getEmail())) {
                continue;
            }
            try {
                emailService.sendAsync("CARE_TASK_OVERDUE", m.getEmail(), "[HorseTrack] " + late.size() + " việc chăm sóc trễ hạn",
                        "<p>Xin chào <b>" + esc(m.getFullName()) + "</b>,</p>" + body);
            } catch (Exception ex) {
                log.warn("Không gửi được cảnh báo SLA tới {}: {}", m.getEmail(), ex.getMessage());
            }
        }
        return late.size();
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
