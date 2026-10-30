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
 * - Việc cho ăn trễ quá app.nutrition.feed-expire-minutes (mặc định 90 phút) sau giờ hẹn mà chưa làm: tự hủy (SKIPPED)
 *   và nhập lại kho thức ăn của bữa đó nếu kho đã cấp cho ngày.
 * Việc cho ăn được đánh dấu bằng ghi chú bắt đầu "KP-planId-MEAL:" (không dùng ký tự [ vì là ký tự đặc biệt của LIKE).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NutritionFeedTaskService {

    public static final List<String> MEALS = List.of("MORNING", "NOON", "EVENING");
    private static final Pattern MARKER = Pattern.compile("^KP-(\\d+)-(MORNING|NOON|EVENING):");
    private static final Map<String, String> MEAL_LABEL = Map.of("MORNING", "sáng", "NOON", "trưa", "EVENING", "tối");
    // Ghi chú của việc cho ăn bị tự hủy do trễ; bữa đó không được sinh lại
    public static final String EXPIRED_TAG = "Tự hủy do trễ";
    private static final List<String> OPEN = List.of("TODO", "IN_PROGRESS");

    private final CareTaskRepository careTaskRepository;
    private final GroomProfileRepository groomProfileRepository;
    private final NutritionPlanRepository planRepository;
    private final StockLedgerService stockLedger;
    private final EntityManager entityManager;
    private final NutritionRequisitions requisitions;
    private final com.horsetrack.equinecare.modules.care.service.HorseScheduleGuard scheduleGuard;

    @Value("${app.nutrition.morning-time:07:00}")
    private String morningTime;
    @Value("${app.nutrition.noon-time:11:30}")
    private String noonTime;
    @Value("${app.nutrition.evening-time:17:00}")
    private String eveningTime;
    @Value("${app.nutrition.feed-expire-minutes:90}")
    private long expireMinutes;

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
        // Kho chưa cấp thức ăn của ngày này thì chưa giao việc cho ăn (NutritionStockService.issueDay giao ngay khi kho cấp)
        if (!requisitions.isDayIssued(plan.getPlanId(), date)) {
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
                    .anyMatch(t -> t.getNotes() != null && t.getNotes().startsWith(mark)
                            && (!"SKIPPED".equals(t.getStatus()) || t.getNotes().contains(EXPIRED_TAG)));
            if (exists) {
                continue;
            }
            String instructions = items.stream()
                    .map(d -> (d.getInventoryItem() != null ? d.getInventoryItem().getItemName() : "Thức ăn") + ": " + fmt(d.getQuantity())
                            + (d.getInventoryItem() != null && d.getInventoryItem().getUnit() != null ? " " + d.getInventoryItem().getUnit() : ""))
                    .collect(Collectors.joining("\n"));
            // Buổi tập đã xếp trước trùng giờ bữa ăn: dời việc cho ăn sang ngay sau buổi tập
            LocalTime at = scheduleGuard.firstFreeCareTime(horse.getHorseId(), date, time);
            String moved = at.equals(time) ? "" : " | Dời từ " + time + " sang " + at + " do trùng buổi tập";
            String title = "Cho ăn bữa " + MEAL_LABEL.get(meal) + " - " + horse.getHorseName()
                    + ("MEDICAL".equals(plan.getPlanType()) ? " (khẩu phần y tế)" : "");
            careTaskRepository.save(CareTask.builder()
                    .horse(horse)
                    .groom(grooms.get(0).getUser())
                    .stall(horse.getStall())
                    .taskType("FEED")
                    .title(title.length() > 150 ? title.substring(0, 150) : title)
                    .taskDate(date)
                    .scheduledTime(at)
                    .instructions(instructions)
                    .dietNote(truncate(plan.getSpecialInstructions(), 255))
                    .isMedicalDiet("MEDICAL".equals(plan.getPlanType()))
                    .notes(mark + " Khẩu phần #" + plan.getPlanId() + moved)
                    .status("TODO")
                    .createdAt(LocalDateTime.now())
                    .build());
            created++;
        }
        return created;
    }

    /**
     * Gửi việc cho ăn khi lập / sửa khẩu phần: các bữa còn lại của hôm nay và cả ngày mai.
     * Lập khẩu phần buổi tối (đã qua bữa tối) thì Groom vẫn thấy ngay lịch cho ăn ngày mai, không phải chờ job 00:05.
     */
    @Transactional
    public int generateUpcoming(NutritionPlan plan) {
        LocalDate today = LocalDate.now();
        return generateForPlan(plan, today, true) + generateForPlan(plan, today.plusDays(1), false);
    }

    /** Khẩu phần bị hủy / thay thế / kết thúc: các việc cho ăn chưa làm từ hôm nay trở đi chuyển SKIPPED */
    @Transactional
    public int skipOpenTasks(NutritionPlan plan, String reason) {
        return skipOpenTasks(plan, reason, LocalDate.now());
    }

    /** Hủy việc cho ăn chưa làm của khẩu phần từ ngày fromDate trở đi */
    @Transactional
    public int skipOpenTasks(NutritionPlan plan, String reason, LocalDate fromDate) {
        // Báo kho: hủy phiếu cấp thức ăn từ ngày này, phiếu đã cấp thì nhập lại phần chưa dùng
        requisitions.cancelForPlan(plan, reason, fromDate);
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

    public long getExpireMinutes() {
        return expireMinutes;
    }

    /** Việc cho ăn theo thực đơn còn mở mà đã trễ quá expireMinutes phút sau giờ hẹn */
    public boolean isExpired(CareTask t, LocalDateTime now) {
        return "FEED".equals(t.getTaskType()) && OPEN.contains(t.getStatus())
                && t.getNotes() != null && MARKER.matcher(t.getNotes()).find()
                && t.getTaskDate() != null && t.getScheduledTime() != null
                && t.getTaskDate().atTime(t.getScheduledTime()).plusMinutes(expireMinutes).isBefore(now);
    }

    /** Định kỳ (mặc định 5 phút): hủy việc cho ăn trễ quá hạn và trả thức ăn về kho */
    @Scheduled(fixedDelayString = "${app.nutrition.feed-expire-check-ms:300000}", initialDelayString = "${app.nutrition.feed-expire-initial-delay-ms:90000}")
    @Transactional
    public void expireLateFeedsJob() {
        int n = expireLateFeeds(LocalDateTime.now());
        if (n > 0) {
            log.warn("Đã tự hủy {} việc cho ăn trễ quá {} phút", n, expireMinutes);
        }
    }

    /** Hủy các việc cho ăn trễ quá hạn tại thời điểm now; kho đã cấp thức ăn của ngày thì nhập lại đúng lượng của bữa. Trả về số việc bị hủy. */
    @Transactional
    public int expireLateFeeds(LocalDateTime now) {
        List<CareTask> late = careTaskRepository.findByTaskTypeAndStatusInAndTaskDateLessThanEqual("FEED", OPEN, now.toLocalDate()).stream()
                .filter(t -> isExpired(t, now))
                .toList();
        List<String> rows = new ArrayList<>();
        for (CareTask t : late) {
            Matcher m = MARKER.matcher(t.getNotes());
            m.find();
            NutritionPlan plan = planRepository.findById(Integer.parseInt(m.group(1))).orElse(null);
            String meal = m.group(2);
            List<String> back = new ArrayList<>();
            if (plan != null && requisitions.isDayIssued(plan.getPlanId(), t.getTaskDate())) {
                for (Map.Entry<Integer, double[]> e : mealItems(plan, meal).entrySet()) {
                    double qty = StockLedgerService.round2(e.getValue()[0]);
                    if (qty <= 0) {
                        continue;
                    }
                    var tx = stockLedger.returnStock(e.getKey(), qty, "TASK", t.getTaskId(),
                            "Nhập lại thức ăn bữa " + MEAL_LABEL.get(meal) + " bị hủy do trễ: " + t.getTaskCode() + " - " + t.getHorse().getHorseName());
                    back.add(StockLedgerService.fmt(qty) + " " + tx.getItem().getUnit() + " " + tx.getItem().getItemName());
                }
            }
            t.setStatus("SKIPPED");
            t.setNotes(truncate(t.getNotes() + " | " + EXPIRED_TAG + " quá " + expireMinutes + " phút (lúc "
                    + now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM")) + ")"
                    + (back.isEmpty() ? "" : ", nhập lại kho " + String.join("; ", back)), 1000));
            careTaskRepository.save(t);
            rows.add(t.getTaskCode() + " (ngựa " + t.getHorse().getHorseName() + ", bữa " + MEAL_LABEL.get(meal) + " " + t.getTaskDate() + ")"
                    + (back.isEmpty() ? "" : ": nhập lại " + String.join("; ", back)));
        }
        if (!rows.isEmpty()) {
            requisitions.notifyWarehouse("NUTRITION_STOCK", late.size() + " bữa ăn bị hủy do trễ quá " + expireMinutes + " phút",
                    "Groom chưa cho ăn sau " + expireMinutes + " phút kể từ giờ hẹn, hệ thống tự hủy: " + String.join(" · ", rows) + ".");
        }
        return late.size();
    }

    /** Định lượng từng món của một bữa trong thực đơn (itemId -> tổng lượng) */
    private static Map<Integer, double[]> mealItems(NutritionPlan plan, String meal) {
        Map<Integer, double[]> perItem = new LinkedHashMap<>();
        for (NutritionPlanDetail d : plan.getDetails()) {
            if (meal.equals(d.getMealType()) && d.getInventoryItem() != null) {
                perItem.computeIfAbsent(d.getInventoryItem().getItemId(), k -> new double[1])[0] += d.getQuantity();
            }
        }
        return perItem;
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
        // Kho đã cấp thức ăn của ngày qua phiếu cấp: chỉ ghi chi phí vật tư, không trừ kho lần nữa
        boolean issuedByWarehouse = requisitions.isDayIssued(plan.getPlanId(), task.getTaskDate());
        for (Map.Entry<Integer, double[]> e : perItem.entrySet()) {
            double qty = Math.round(e.getValue()[0] * 100.0) / 100.0;
            try {
                if (!issuedByWarehouse) {
                    stockLedger.issue(e.getKey(), qty, null, "ISSUE", "TASK", task.getTaskId(), "Cho ăn " + task.getTaskCode() + " - " + task.getHorse().getHorseName());
                }
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
