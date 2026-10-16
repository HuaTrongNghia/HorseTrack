package com.horsetrack.equinecare.modules.care.service.impl;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.care.dto.CareTaskDTO;
import com.horsetrack.equinecare.modules.care.entity.CareTask;
import com.horsetrack.equinecare.modules.care.entity.NutritionPlan;
import com.horsetrack.equinecare.modules.care.repository.CareTaskRepository;
import com.horsetrack.equinecare.modules.care.repository.NutritionPlanRepository;
import com.horsetrack.equinecare.modules.care.service.CareTaskService;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryItemRepository;
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

    private User getLoggedInUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareTaskDTO> getMyTasksToday() {
        User user = getLoggedInUser();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);

        return careTaskRepository.findByAssignedTo_UserIdAndDueDateBetweenOrderByDueDateAsc(user.getUserId(), startOfDay, endOfDay)
                .stream().map(task -> CareTaskDTO.builder()
                        .taskId(task.getTaskId())
                        .taskType(task.getTaskType())
                        .status(task.getStatus())
                        .dueDate(task.getDueDate())
                        .horseName(task.getHorse() != null ? task.getHorse().getHorseName() : null)
                        .stallZone(task.getHorse() != null && task.getHorse().getStall() != null ? task.getHorse().getStall().getBarnZone() : null)
                        .build()
                ).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(Integer taskId) {
        User user = getLoggedInUser();
        CareTask task = careTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy công việc"));

        if (!task.getAssignedTo().getUserId().equals(user.getUserId())) {
            throw new AccessDeniedException("Bạn không được phân công công việc này");
        }

        if ("COMPLETED".equals(task.getStatus())) {
            throw new RuntimeException("Công việc này đã được đánh dấu hoàn thành trước đó");
        }

        // 1. Cập nhật trạng thái Task
        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        careTaskRepository.save(task);

        // 2. THUẬT TOÁN TRỪ KHO TỰ ĐỘNG NẾU LÀ VIỆC CHO ĂN (FEEDING)
        if ("FEEDING".equalsIgnoreCase(task.getTaskType())) {
            List<NutritionPlan> plans = nutritionPlanRepository.findByHorse_HorseId(task.getHorse().getHorseId());
            for (NutritionPlan plan : plans) {
                InventoryItem item = plan.getInventoryItem();
                if (item != null && item.getCurrentStock() != null) {
                    // Trừ số lượng tồn kho dựa trên khẩu phần ăn của con ngựa đó
                    int newStock = item.getCurrentStock() - plan.getQuantityPerMeal();
                    item.setCurrentStock(Math.max(newStock, 0)); // Đảm bảo kho không bị âm
                    inventoryItemRepository.save(item);
                }
            }
        }
    }
}