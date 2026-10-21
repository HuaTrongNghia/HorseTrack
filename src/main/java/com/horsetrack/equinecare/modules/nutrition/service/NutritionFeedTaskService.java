package com.horsetrack.equinecare.modules.nutrition.service;

import com.horsetrack.equinecare.modules.auth.entity.GroomProfile;
import com.horsetrack.equinecare.modules.auth.repository.GroomProfileRepository;
import com.horsetrack.equinecare.modules.care.entity.CareTask;
import com.horsetrack.equinecare.modules.care.repository.CareTaskRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.inventory.service.StockLedgerService;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlan;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlanDetail;
import com.horsetrack.equinecare.modules.nutrition.repository.NutritionPlanRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * "Gửi khẩu phần cho nhân viên": mỗi bữa của khẩu phần đang áp dụng thành một việc cho ăn (Daily_Tasks, FEED)
 * giao cho Groom phụ trách khu chuồng của ngựa, kèm định lượng từng món và lưu ý đặc biệt.
 * - Tự tạo cho hôm nay khi lập / sửa khẩu phần, và hằng ngày lúc 00:05 cho mọi khẩu phần đang áp dụng.
 * - Groom hoàn thành việc cho ăn thì trừ kho đúng các món của bữa đó qua sổ cái kho và ghi chi phí vật tư
 *   (Task_Material_Usage, dùng cho hóa đơn tháng - dòng DAILY_CARE).
 * Việc cho ăn được đánh dấu bằng ghi chú bắt đầu "KP-planId-MEAL:" (không dùng ký tự [ vì là ký tự đặc biệt của LIKE).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NutritionFeedTaskService {

    public static final List<String> MEALS = List.of("MORNING", "NOON", "EVENING");
    private static final Pattern MARKER = Pattern.compile("^KP-(\\d+)-(MORNING|NOON|EVENING):");
    private static final Map<String, String> MEAL_LABEL = Map.of("MORNING", "sáng", "NOON", "trưa", "EVENING", "tối");

    private final CareTaskRepository careTaskRepository;
    private final GroomProfileRepository groomProfileRepository;
    private final NutritionPlanRepository planRepository;
    private final StockLedgerService stockLedger;
    private final EntityManager entityManager;

    @Value("${app.nutrition.morning-time:07:00}")
    private String morningTime;
    @Value("${app.nutrition.noon-time:11:30}")
    private String noonTime;
    @Value("${app.nutrition.evening-time:17:00}")
    private String eveningTime;

    public LocalTime mealTime(String meal) {
        return LocalTime.parse(switch (meal) {
            case "MORNING" -> morningTime;
            case "NOON" -> noonTime;
            default -> eveningTime;
        });
    }

    static String marker(Integer planId, String meal) {
        return "KP-" + planId + "-" + meal + ":";
    }

    /** Hằng ngày 00:05: gửi việc cho ăn của hôm nay cho mọi khẩu phần đang áp dụng */
    @Scheduled(cron = "${app.nutrition.feed-task-cron:0 5 0 * * *}")
    @Transactional
    public void generateDaily() {
        int n = generateForDate(LocalDate.now());
        if (n > 0) {
            log.info("Đã gửi {} việc cho ăn cho Groom", n);
        }
    }

    @Transactional
    public int generateForDate(LocalDate date) {
        int n = 0;
        for (NutritionPlan p : planRepository.findByStatusOrderByCreatedDateDesc("ACTIVE")) {
            if (!date.isBefore(p.getStartDate()) && !date.isAfter(p.getEndDate())) {
                n += generateForPlan(p, date, false);
            }
        }
        return n;
    }

    /**
     * Tạo việc cho ăn của một ngày cho khẩu phần. onlyUpcoming = true: hôm nay chỉ tạo các bữa chưa tới giờ.
     * Bữa đã có việc (chưa bị hủy) thì bỏ qua. Trả về số việc vừa tạo.
     */
    @Transactional
    public int generateForPlan(NutritionPlan plan, LocalDate date, boolean onlyUpcoming) {
        if (!"ACTIVE".equals(plan.getStatus()) || date.isBefore(plan.getStartDate()) || date.isAfter(plan.getEndDate())) {
            return 0;
        }
        Horse horse = plan.getHorse();
        if (horse.getStall() == null) {
            return 0;
        }
        List<GroomProfile> grooms = groomProfileRepository.findActiveGroomsInZone(horse.getStall().getBarnZone());
        if (grooms.isEmpty()) {
            log.warn("Khẩu phần #{}: khu {} chưa có Groom, chưa gửi được việc cho ăn", plan.getPlanId(), horse.getStall().getBarnZone());
            return 0;
        }
        Map<String, List<NutritionPlanDetail>> byMeal = plan.getDetails().stream()
                .collect(Collectors.groupingBy(NutritionPlanDetail::getMealType, LinkedHashMap::new, Collectors.toList()));
        int created = 0;
        for (String meal : MEALS) {
            List<NutritionPlanDetail> items = byMeal.get(meal);
            if (items == null || items.isEmpty()) {
                continue;
            }
            LocalTime time = mealTime(meal);
            if (onlyUpcoming && date.equals(LocalDate.now()) && time.isBefore(LocalTime.now())) {
                continue;
            }
            String mark = marker(plan.getPlanId(), meal);
            boolean exists = careTaskRepository.findByHorse_HorseIdAndTaskDateAndTaskType(horse.getHorseId(), date, "FEED").stream()
                    .anyMatch(t -> t.getNotes() != null && t.getNotes().startsWith(mark) && !"SKIPPED".equals(t.getStatus()));
            if (exists) {
                continue;
            }
            String instructions = items.stream()
                    .map(d -> (d.getInventoryItem() != null ? d.getInventoryItem().getItemName() : "Thức ăn") + ": " + fmt(d.getQuantity())
                            + (d.getInventoryItem() != null && d.getInventoryItem().getUnit() != null ? " " + d.getInventoryItem().getUnit() : ""))
                    .collect(Collectors.joining("\n"));
            String title = "Cho ăn bữa " + MEAL_LABEL.get(meal) + " - " + horse.getHorseName()
                    + ("MEDICAL".equals(plan.getPlanType()) ? " (khẩu phần y tế)" : "");
            careTaskRepository.save(CareTask.builder()
                    .horse(horse)
                    .groom(grooms.get(0).getUser())
                    .stall(horse.getStall())
                    .taskType("FEED")
                    .title(title.length() > 150 ? title.substring(0, 150) : title)
                    .taskDate(date)
                    .scheduledTime(time)
                    .instructions(instructions)
                    .dietNote(truncate(plan.getSpecialInstructions(), 255))
                    .isMedicalDiet("MEDICAL".equals(plan.getPlanType()))
                    .notes(mark + " Khẩu phần #" + plan.getPlanId())
                    .status("TODO")
                    .createdAt(LocalDateTime.now())
                    .build());
            created++;
        }
        return created;
    }

    /** Khẩu phần bị hủy / thay thế / kết thúc: các việc cho ăn chưa làm từ hôm nay trở đi chuyển SKIPPED */
    @Transactional
    public int skipOpenTasks(NutritionPlan plan, String reason) {
        return skipOpenTasks(plan, reason, LocalDate.now());
    }

    /** Hủy việc cho ăn chưa làm của khẩu phần từ ngày fromDate trở đi */
    @Transactional
    public int skipOpenTasks(NutritionPlan plan, String reason, LocalDate fromDate) {
        int n = 0;
        LocalDate from = fromDate.isBefore(LocalDate.now()) ? LocalDate.now() : fromDate;
        for (CareTask t : careTaskRepository.findByNotesStartingWithAndStatusAndTaskDateGreaterThanEqual("KP-" + plan.getPlanId() + "-", "TODO", from)) {
            t.setStatus("SKIPPED");
            t.setNotes(truncate(t.getNotes() + " | Hủy: " + reason, 1000));
            careTaskRepository.save(t);
            n++;
        }
        return n;
    }

    /** Cập nhật lưu ý đặc biệt cho các việc cho ăn chưa làm của khẩu phần (sau khi bác sĩ duyệt đề xuất thức ăn) */
    @Transactional
    public void refreshDietNote(NutritionPlan plan) {
        for (CareTask t : careTaskRepository.findByNotesStartingWithAndStatusAndTaskDateGreaterThanEqual("KP-" + plan.getPlanId() + "-", "TODO", LocalDate.now())) {
            t.setDietNote(truncate(plan.getSpecialInstructions(), 255));
            careTaskRepository.save(t);
        }
    }

    /**
     * Groom hoàn thành việc cho ăn: trừ kho đúng các món của bữa đó (qua sổ cái, có kiểm tra khả dụng) và ghi chi phí vật tư.
     * Thiếu hàng không chặn việc hoàn thành; lý do được ghi vào ghi chú của việc.
     */
    @Transactional
    public void consumeStock(CareTask task) {
        Matcher m = task.getNotes() == null ? null : MARKER.matcher(task.getNotes());
        if (m == null || !m.find()) {
            return;
        }
        NutritionPlan plan = planRepository.findById(Integer.parseInt(m.group(1))).orElse(null);
        if (plan == null) {
            return;
        }
        String meal = m.group(2);
        Map<Integer, double[]> perItem = new LinkedHashMap<>();
        Map<Integer, java.math.BigDecimal> prices = new HashMap<>();
        for (NutritionPlanDetail d : plan.getDetails()) {
            if (meal.equals(d.getMealType()) && d.getInventoryItem() != null) {
                perItem.computeIfAbsent(d.getInventoryItem().getItemId(), k -> new double[1])[0] += d.getQuantity();
                prices.put(d.getInventoryItem().getItemId(), d.getInventoryItem().getUnitPrice() == null ? java.math.BigDecimal.ZERO
                        : java.math.BigDecimal.valueOf(d.getInventoryItem().getUnitPrice().doubleValue()));
            }
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<Integer, double[]> e : perItem.entrySet()) {
            double qty = Math.round(e.getValue()[0] * 100.0) / 100.0;
            try {
                stockLedger.issue(e.getKey(), qty, null, "ISSUE", "TASK", task.getTaskId(), "Cho ăn " + task.getTaskCode() + " - " + task.getHorse().getHorseName());
                // Chi phí vật tư chăm sóc, dùng cho hóa đơn tháng (DAILY_CARE)
                entityManager.createNativeQuery("INSERT INTO Task_Material_Usage (task_id, item_id, quantity_used, unit_price_at_use) VALUES (?1, ?2, ?3, ?4)")
                        .setParameter(1, task.getTaskId()).setParameter(2, e.getKey()).setParameter(3, qty).setParameter(4, prices.get(e.getKey()))
                        .executeUpdate();
            } catch (Exception ex) {
                problems.add(ex.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            task.setNotes(truncate(task.getNotes() + " | Chưa trừ kho: " + String.join("; ", problems), 1000));
            careTaskRepository.save(task);
            log.warn("Việc cho ăn {}: chưa trừ kho được - {}", task.getTaskCode(), problems);
        }
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String truncate(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
