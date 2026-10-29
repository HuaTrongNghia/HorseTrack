package com.horsetrack.equinecare.modules.care.service.impl;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.entity.GroomProfile;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.GroomProfileRepository;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.care.dto.CareTaskCreateRequestDTO;
import com.horsetrack.equinecare.modules.care.dto.CareTaskDTO;
import com.horsetrack.equinecare.modules.care.entity.CareTask;
import com.horsetrack.equinecare.modules.care.repository.CareTaskRepository;
import com.horsetrack.equinecare.modules.care.service.CareTaskService;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryItemRepository;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlan;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlanDetail;
import com.horsetrack.equinecare.modules.nutrition.repository.NutritionPlanRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CareTaskServiceImpl implements CareTaskService {

    private final CareTaskRepository careTaskRepository;
    private final NutritionPlanRepository nutritionPlanRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final UserRepository userRepository;
    private final HorseRepository horseRepository;
    private final EntityManager entityManager;
    private final GroomProfileRepository groomProfileRepository;
    private final com.horsetrack.equinecare.modules.horse.service.HorseAccessGuard horseAccessGuard;
    private final com.horsetrack.equinecare.modules.nutrition.service.NutritionFeedTaskService feedTaskService;
    private final com.horsetrack.equinecare.modules.notification.service.AppNotificationService appNotifications;
    private final com.horsetrack.equinecare.modules.audit.service.AuditTrail auditTrail;
    private final com.horsetrack.equinecare.modules.nutrition.service.StallService stallService;

    // Việc chưa xong (còn có thể bị cảnh báo trễ)
    static final List<String> OPEN_STATUSES = List.of("TODO", "IN_PROGRESS");

    @org.springframework.beans.factory.annotation.Value("${app.care.sla-minutes:45}")
    private long slaMinutes;

    @org.springframework.beans.factory.annotation.Value("${app.care.early-complete-minutes:30}")
    private long earlyCompleteMinutes;

    @Override
    @Transactional(readOnly = true)
    public List<CareTaskDTO> getOverdueTasks() {
        return careTaskRepository.findByWarningNoteIsNotNullAndStatusInOrderByTaskDateAscScheduledTimeAsc(OPEN_STATUSES).stream()
                .map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareTaskDTO> getLateTasksOn(LocalDate date) {
        return careTaskRepository.findByTaskDateAndWarningNoteIsNotNullAndStatusNotOrderByScheduledTimeAsc(date != null ? date : LocalDate.now(), "SKIPPED").stream()
                .map(this::mapToDTO).collect(Collectors.toList());
    }

    private User getLoggedInUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
    }

    // Báo HLV phụ trách qua chuông (không mail — tránh spam). Việc ăn KP- bỏ qua.
    private void notifyTrainerDone(CareTask task, User groom) {
        Horse horse = task.getHorse();
        User trainer = horse != null ? horse.getAssignedTrainer() : null;
        if (trainer == null || (task.getNotes() != null && task.getNotes().startsWith("KP-"))) {
            return;
        }
        String when = task.getCompletedAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
        String title = task.getTitle() != null ? task.getTitle() : task.getTaskType();
        appNotifications.push(trainer, "CARE_TASK_DONE",
                "Đã hoàn thành: " + title + " — " + horse.getHorseName(),
                "Mã phu " + groom.getFullName() + " đã xong việc \"" + title + "\" cho ngựa " + horse.getHorseName()
                        + " (lịch " + task.getScheduledTime() + " ngày " + task.getTaskDate() + ") lúc " + when + ".",
                "/trainer");
    }

    // Thời điểm sớm nhất được xác nhận hoàn thành: giờ hẹn trừ khung xác nhận sớm; việc không có giờ hẹn thì từ đầu ngày của việc
    private LocalDateTime completableFrom(CareTask task) {
        if (task.getTaskDate() == null) {
            return null;
        }
        return task.getScheduledTime() != null
                ? task.getTaskDate().atTime(task.getScheduledTime()).minusMinutes(earlyCompleteMinutes)
                : task.getTaskDate().atStartOfDay();
    }

    // Số phút trễ so với giờ hẹn nếu đã quá SLA (tới lúc hoàn thành, hoặc tới hiện tại khi chưa xong); null nếu chưa trễ SLA
    private Integer lateMinutes(CareTask task) {
        if (task.getTaskDate() == null || task.getScheduledTime() == null || "SKIPPED".equals(task.getStatus())) {
            return null;
        }
        LocalDateTime due = task.getTaskDate().atTime(task.getScheduledTime());
        LocalDateTime end = task.getCompletedAt() != null ? task.getCompletedAt() : LocalDateTime.now();
        long minutes = java.time.Duration.between(due, end).toMinutes();
        return minutes > slaMinutes ? (int) minutes : null;
    }

    private CareTaskDTO mapToDTO(CareTask task) {
        Horse horse = task.getHorse();
        return CareTaskDTO.builder()
                .taskId(task.getTaskId())
                .taskCode(task.getTaskCode())
                .taskType(task.getTaskType())
                .title(task.getTitle())
                .status(task.getStatus())
                .taskDate(task.getTaskDate())
                .scheduledTime(task.getScheduledTime())
                .instructions(task.getInstructions())
                .completedAt(task.getCompletedAt())
                .horseId(horse != null ? horse.getHorseId() : null)
                .horseName(horse != null ? horse.getHorseName() : null)
                .stallCode(task.getStall() != null ? task.getStall().getStallCode() : null)
                .stallZone(task.getStall() != null ? task.getStall().getBarnZone() : null)
                .groomId(task.getGroom() != null ? task.getGroom().getUserId() : null)
                .groomName(task.getGroom() != null ? task.getGroom().getFullName() : null)
                .warningNote(task.getWarningNote())
                .overdue(task.getWarningNote() != null && OPEN_STATUSES.contains(task.getStatus()))
                .lateMinutes(lateMinutes(task))
                .dietNote(task.getDietNote())
                .medicalDiet(task.getIsMedicalDiet())
                .completableFrom(completableFrom(task))
                .canComplete(OPEN_STATUSES.contains(task.getStatus())
                        && (completableFrom(task) == null || !LocalDateTime.now().isBefore(completableFrom(task))))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareTaskDTO> getMyTasksToday() {
        return getMyTasks(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareTaskDTO> getMyTasks(LocalDate date) {
        User user = getLoggedInUser();
        return careTaskRepository.findByGroom_UserIdAndTaskDateOrderByScheduledTimeAsc(user.getUserId(), date != null ? date : LocalDate.now())
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareTaskDTO> getAssignedTasks(LocalDate date, String status) {
        User user = getLoggedInUser();
        LocalDate day = date != null ? date : LocalDate.now();
        List<CareTask> tasks = "Trainer".equals(user.getRoleName())
                ? careTaskRepository.findByHorse_AssignedTrainer_UserIdAndTaskDateOrderByScheduledTimeAsc(user.getUserId(), day)
                : careTaskRepository.findByTaskDateOrderByScheduledTimeAsc(day);
        String st = status != null && !status.isBlank() ? status.trim().toUpperCase() : null;
        return tasks.stream().filter(t -> st == null || st.equals(t.getStatus())).map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(Integer taskId) {
        User user = getLoggedInUser();
        CareTask task = careTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với ID: " + taskId));

        if (task.getGroom() != null && !task.getGroom().getUserId().equals(user.getUserId())) {
            throw new AccessDeniedException("Bạn không được phân công công việc này");
        }

        if ("SKIPPED".equals(task.getStatus())) {
            throw new IllegalArgumentException("Công việc này đã bị hủy (khẩu phần đã thay đổi).");
        }
        if ("DONE".equals(task.getStatus())) {
            throw new IllegalArgumentException("Công việc này đã được đánh dấu hoàn thành trước đó");
        }
        // Chưa tới giờ hẹn (trừ khung xác nhận sớm) thì chưa được hoàn thành, kể cả việc ngày mai đã gửi trước
        LocalDateTime from = completableFrom(task);
        if (from != null && LocalDateTime.now().isBefore(from)) {
            throw new IllegalArgumentException("Chưa tới giờ thực hiện: việc hẹn " + (task.getScheduledTime() != null ? task.getScheduledTime() + " " : "")
                    + task.getTaskDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ", chỉ được xác nhận hoàn thành từ "
                    + from.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM")) + ".");
        }

        task.setStatus("DONE");
        task.setCompletedAt(LocalDateTime.now());
        // BR_05: hoàn thành sau giờ hẹn + SLA thì ghi nhận trễ (kể cả khi SLA Monitor chưa kịp cảnh báo) để Club Manager thống kê
        Integer late = lateMinutes(task);
        if (late != null) {
            String done = "Hoàn thành trễ " + late + " phút (hẹn " + task.getScheduledTime() + ", xong "
                    + task.getCompletedAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM")) + ")";
            String note = task.getWarningNote() == null ? done : task.getWarningNote() + " | " + done;
            task.setWarningNote(note.length() > 500 ? note.substring(0, 500) : note);
        }
        careTaskRepository.save(task);

        auditTrail.record("COMPLETE_CARE_TASK", "Daily_Tasks", "status=TODO",
                "status=DONE | " + (task.getTaskCode() != null ? task.getTaskCode() : "#" + task.getTaskId()) + " | " + task.getTaskType()
                        + (task.getHorse() != null ? " | ngựa " + task.getHorse().getHorseName() : "") + " | Groom " + user.getFullName());
        notifyTrainerDone(task, user);

        // Việc cho ăn sinh từ khẩu phần: trừ kho đúng các món của bữa đó qua sổ cái và ghi chi phí vật tư
        if ("FEED".equalsIgnoreCase(task.getTaskType()) && task.getHorse() != null) {
            feedTaskService.consumeStock(task);
        }
        // Dọn chuồng xong: chuồng của việc (hoặc chuồng hiện tại của ngựa) chuyển CLEAN
        if ("CLEAN".equalsIgnoreCase(task.getTaskType())) {
            stallService.markCleanedByTask(task.getStall() != null ? task.getStall() : task.getHorse() != null ? task.getHorse().getStall() : null,
                    "việc dọn chuồng #" + task.getTaskId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareTaskDTO createTask(CareTaskCreateRequestDTO request) {
        Horse horse = horseRepository.findById(request.getHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + request.getHorseId()));
        // Trainer chỉ giao việc chăm sóc cho ngựa được Club Manager phân công cho mình
        horseAccessGuard.checkAssigned(horse);

        // Ngựa chưa được duyệt (chưa có chuồng) thì chưa thuộc CLB chăm sóc
        if (horse.getStall() == null) {
            throw new IllegalArgumentException("Ngựa chưa được duyệt và cấp chuồng, chưa thể giao việc chăm sóc.");
        }

        User groom = userRepository.findById(request.getGroomId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên với ID: " + request.getGroomId()));

        if (!"Groom".equals(groom.getRoleName())) {
            throw new IllegalArgumentException("Chỉ được giao việc chăm sóc cho nhân viên có vai trò Groom.");
        }
        if (Boolean.FALSE.equals(groom.getIsActive())) {
            throw new IllegalArgumentException("Tài khoản Groom này đã bị vô hiệu hóa.");
        }
        com.horsetrack.equinecare.modules.auth.service.StaffAccountService.requireConfirmed(groom, "giao việc chăm sóc");

        // F4-03 (BR35): Groom chỉ chăm sóc ngựa thuộc khu chuồng mình phụ trách
        String horseZone = horse.getStall().getBarnZone();
        String groomZone = groomProfileRepository.findById(groom.getUserId()).map(GroomProfile::getAssignedBarnZone).orElse(null);
        if (horseZone == null || groomZone == null || !horseZone.trim().equalsIgnoreCase(groomZone.trim())) {
            throw new IllegalArgumentException("Groom " + groom.getFullName() + " phụ trách khu '" + (groomZone == null ? "chưa có" : groomZone)
                    + "', không phải khu '" + horseZone + "' của ngựa " + horse.getHorseName() + ".");
        }

        CareTask task = CareTask.builder()
                .horse(horse)
                .groom(groom)
                .stall(horse.getStall())
                .taskType(request.getTaskType().toUpperCase())
                .title(request.getTitle())
                .taskDate(request.getTaskDate())
                .scheduledTime(request.getScheduledTime())
                .instructions(request.getInstructions())
                .isMedicalDiet(false)
                .status("TODO")
                .createdAt(LocalDateTime.now())
                .build();

        CareTask saved = careTaskRepository.saveAndFlush(task);
        // Đọc lại từ DB để lấy task_code do DB tự sinh
        entityManager.refresh(saved);
        String taskLabel = saved.getTitle() != null ? saved.getTitle() : saved.getTaskType();
        appNotifications.push(groom, "CARE_TASK_ASSIGNED",
                "Việc mới: " + taskLabel + " — " + horse.getHorseName(),
                "Bạn được giao việc \"" + taskLabel + "\" cho ngựa " + horse.getHorseName()
                        + " ngày " + saved.getTaskDate()
                        + (saved.getScheduledTime() != null ? " lúc " + saved.getScheduledTime() : "") + ".",
                "/groom");
        return mapToDTO(saved);
    }
}
