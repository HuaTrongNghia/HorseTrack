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

    // Việc chưa xong (còn có thể bị cảnh báo trễ)
    static final List<String> OPEN_STATUSES = List.of("TODO", "IN_PROGRESS");

    @Override
    @Transactional(readOnly = true)
    public List<CareTaskDTO> getOverdueTasks() {
        return careTaskRepository.findByWarningNoteIsNotNullAndStatusInOrderByTaskDateAscScheduledTimeAsc(OPEN_STATUSES).stream()
                .map(this::mapToDTO).collect(Collectors.toList());
    }

    private User getLoggedInUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
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
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareTaskDTO> getMyTasksToday() {
        User user = getLoggedInUser();
        return careTaskRepository.findByGroom_UserIdAndTaskDateOrderByScheduledTimeAsc(user.getUserId(), LocalDate.now())
                .stream().map(this::mapToDTO).collect(Collectors.toList());
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

        if ("DONE".equals(task.getStatus())) {
            throw new IllegalArgumentException("Công việc này đã được đánh dấu hoàn thành trước đó");
        }

        task.setStatus("DONE");
        task.setCompletedAt(LocalDateTime.now());
        careTaskRepository.save(task);

        // Giảm tồn kho nếu là công việc cho ăn (FEED)
        if ("FEED".equalsIgnoreCase(task.getTaskType()) && task.getHorse() != null) {
            List<NutritionPlan> plans = nutritionPlanRepository.findByHorse_HorseIdOrderByCreatedDateDesc(task.getHorse().getHorseId());
            for (NutritionPlan plan : plans) {
                if (plan.getDetails() != null) {
                    for (NutritionPlanDetail detail : plan.getDetails()) {
                        InventoryItem item = detail.getInventoryItem();
                        if (item != null && item.getCurrentStock() != null) {
                            double newStock = Math.max(0, item.getCurrentStock() - detail.getQuantity());
                            item.setCurrentStock(newStock);
                            inventoryItemRepository.save(item);
                        }
                    }
                }
            }
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
                .status("TODO")
                .createdAt(LocalDateTime.now())
                .build();

        CareTask saved = careTaskRepository.saveAndFlush(task);
        // Đọc lại từ DB để lấy task_code do DB tự sinh
        entityManager.refresh(saved);
        return mapToDTO(saved);
    }
}
