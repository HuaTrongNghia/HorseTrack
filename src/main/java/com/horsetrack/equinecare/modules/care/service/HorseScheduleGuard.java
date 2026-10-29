package com.horsetrack.equinecare.modules.care.service;

import com.horsetrack.equinecare.modules.care.entity.CareTask;
import com.horsetrack.equinecare.modules.care.repository.CareTaskRepository;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlan;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlanDetail;
import com.horsetrack.equinecare.modules.nutrition.repository.NutritionPlanRepository;
import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import com.horsetrack.equinecare.modules.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Một con ngựa không vừa tập vừa được cho ăn / chăm sóc: buổi tập và việc chăm sóc (kể cả bữa ăn của thực đơn) không được trùng khung giờ.
 * Lịch nào có trước được ưu tiên:
 * - Giao việc chăm sóc trùng buổi tập đã xếp → từ chối. Xếp buổi tập trùng việc chăm sóc / bữa ăn đã có → từ chối.
 * - Việc cho ăn tự sinh từ thực đơn mà trùng buổi tập đã xếp trước → dời sang ngay sau buổi tập.
 * - Ngựa đang trong buổi tập thì Groom chưa hoàn thành được việc chăm sóc.
 * Khung giờ: buổi tập app.training.session-duration-minutes (mặc định 60), việc chăm sóc app.care.task-duration-minutes (mặc định 30).
 */
@Component
@RequiredArgsConstructor
public class HorseScheduleGuard {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DMY_HM = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    // Buổi tập còn chiếm lịch của ngựa (chờ xác nhận / đã xác nhận / đang tập / tạm dừng chờ HLV)
    private static final List<String> SESSION_BUSY = List.of("PENDING", "CONFIRMED", "IN_PROGRESS", "PAUSED_PENDING_TRAINER");
    private static final List<String> SESSION_RUNNING = List.of("IN_PROGRESS", "PAUSED_PENDING_TRAINER");
    private static final Set<String> CARE_OPEN = Set.of("TODO", "IN_PROGRESS");

    private final TrainingSessionRepository sessionRepository;
    private final CareTaskRepository careTaskRepository;
    private final NutritionPlanRepository planRepository;

    @Value("${app.training.session-duration-minutes:60}")
    private long sessionMinutes;
    @Value("${app.care.task-duration-minutes:30}")
    private long careMinutes;
    @Value("${app.nutrition.morning-time:07:00}")
    private String morningTime;
    @Value("${app.nutrition.noon-time:11:30}")
    private String noonTime;
    @Value("${app.nutrition.evening-time:17:00}")
    private String eveningTime;

    /** Buổi tập còn chiếm lịch của ngựa giao với khoảng [from, to) */
    @Transactional(readOnly = true)
    public Optional<TrainingSession> sessionClash(Integer horseId, LocalDateTime from, LocalDateTime to) {
        return sessionRepository.findBusyByHorse(horseId, SESSION_BUSY, from.minusMinutes(sessionMinutes), to).stream()
                .filter(s -> s.getSessionDate().plusMinutes(sessionMinutes).isAfter(from) && s.getSessionDate().isBefore(to))
                .findFirst();
    }

    /** Giao việc chăm sóc: không trùng buổi tập đã xếp trước của ngựa */
    @Transactional(readOnly = true)
    public void assertCareSlotFree(Integer horseId, String horseName, LocalDate day, LocalTime time) {
        if (day == null || time == null) {
            return;
        }
        LocalDateTime start = day.atTime(time);
        sessionClash(horseId, start, start.plusMinutes(careMinutes)).ifPresent(s -> {
            throw new IllegalArgumentException("Ngựa " + horseName + " có buổi tập lúc " + s.getSessionDate().format(DMY_HM) + " (" + s.getActivityType()
                    + ", khoảng " + sessionMinutes + " phút) trùng giờ việc chăm sóc " + time.format(HM)
                    + ". Buổi tập đã xếp trước, hãy chọn giờ khác (sau " + s.getSessionDate().plusMinutes(sessionMinutes).format(HM) + ").");
        });
    }

    /** Xếp buổi tập: không trùng việc chăm sóc đang mở hoặc bữa ăn của thực đơn đang áp dụng của ngựa */
    @Transactional(readOnly = true)
    public void assertSessionSlotFree(Integer horseId, String horseName, LocalDateTime start) {
        LocalDateTime end = start.plusMinutes(sessionMinutes);
        LocalDate day = start.toLocalDate();
        List<CareTask> tasks = careTaskRepository.findByHorse_HorseIdAndTaskDate(horseId, day);
        for (CareTask t : tasks) {
            if (CARE_OPEN.contains(t.getStatus()) && t.getScheduledTime() != null && overlaps(day.atTime(t.getScheduledTime()), careMinutes, start, end)) {
                throw new IllegalArgumentException("Ngựa " + horseName + " có việc chăm sóc \"" + (t.getTitle() != null ? t.getTitle() : t.getTaskType())
                        + "\" lúc " + t.getScheduledTime().format(HM) + " ngày " + day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + " trùng buổi tập " + start.format(HM) + "-" + end.format(HM) + ". Việc chăm sóc đã xếp trước, hãy chọn giờ tập khác.");
            }
        }
        // Bữa ăn của thực đơn chưa sinh việc cho ăn (VD: chờ kho cấp) vẫn giữ chỗ theo giờ bữa
        Set<String> mealsWithTask = tasks.stream().filter(t -> "FEED".equals(t.getTaskType()) && t.getNotes() != null && t.getNotes().startsWith("KP-"))
                .map(t -> t.getNotes().replaceFirst("^KP-\\d+-(MORNING|NOON|EVENING):.*$", "$1")).collect(Collectors.toSet());
        for (NutritionPlan plan : planRepository.findActivePlansForHorseOnDate(horseId, day)) {
            for (String meal : plan.getDetails().stream().map(NutritionPlanDetail::getMealType).distinct().toList()) {
                if (mealsWithTask.contains(meal)) {
                    continue;
                }
                LocalTime at = mealTime(meal);
                if (overlaps(day.atTime(at), careMinutes, start, end)) {
                    throw new IllegalArgumentException("Ngựa " + horseName + " có bữa ăn " + mealLabel(meal) + " lúc " + at.format(HM) + " theo thực đơn #" + plan.getPlanId()
                            + " trùng buổi tập " + start.format(HM) + "-" + end.format(HM) + ". Thực đơn đã có trước, hãy chọn giờ tập khác.");
                }
            }
            break;   // chỉ thực đơn đang áp dụng mới nhất
        }
    }

    /** Việc cho ăn tự sinh: trùng buổi tập đã xếp trước thì dời sang ngay sau buổi tập. Trả về giờ dùng được. */
    @Transactional(readOnly = true)
    public LocalTime firstFreeCareTime(Integer horseId, LocalDate day, LocalTime wanted) {
        LocalDateTime start = day.atTime(wanted);
        for (int i = 0; i < 12; i++) {
            Optional<TrainingSession> clash = sessionClash(horseId, start, start.plusMinutes(careMinutes));
            if (clash.isEmpty()) {
                break;
            }
            start = clash.get().getSessionDate().plusMinutes(sessionMinutes);
        }
        return start.toLocalDate().equals(day) ? start.toLocalTime() : LocalTime.of(23, 59);
    }

    /** Groom hoàn thành việc chăm sóc: ngựa không được đang trong buổi tập */
    @Transactional(readOnly = true)
    public void assertNotTrainingNow(Integer horseId, String horseName) {
        sessionRepository.findBusyByHorse(horseId, SESSION_RUNNING, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)).stream().findFirst()
                .ifPresent(s -> {
                    throw new IllegalArgumentException("Ngựa " + horseName + " đang trong buổi tập (bắt đầu " + s.getSessionDate().format(DMY_HM)
                            + ", " + s.getStatus() + "). Chờ buổi tập kết thúc rồi mới cho ăn / chăm sóc.");
                });
    }

    private static boolean overlaps(LocalDateTime aStart, long aMinutes, LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && aStart.plusMinutes(aMinutes).isAfter(bStart);
    }

    private LocalTime mealTime(String meal) {
        return LocalTime.parse(switch (meal) {
            case "MORNING" -> morningTime;
            case "NOON" -> noonTime;
            default -> eveningTime;
        });
    }

    private static String mealLabel(String meal) {
        return switch (meal) {
            case "MORNING" -> "sáng";
            case "NOON" -> "trưa";
            default -> "tối";
        };
    }
}
