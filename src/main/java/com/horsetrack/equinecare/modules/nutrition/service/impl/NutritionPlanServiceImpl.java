package com.horsetrack.equinecare.modules.nutrition.service.impl;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryItemRepository;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionPlanCreateRequestDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionPlanDetailCreateDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionTodayItemDTO;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlan;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlanDetail;
import com.horsetrack.equinecare.modules.nutrition.repository.NutritionPlanDetailRepository;
import com.horsetrack.equinecare.modules.nutrition.repository.NutritionPlanRepository;
import com.horsetrack.equinecare.modules.nutrition.service.NutritionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NutritionPlanServiceImpl implements NutritionPlanService {

    private final NutritionPlanRepository nutritionPlanRepository;
    private final NutritionPlanDetailRepository nutritionPlanDetailRepository;
    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final InventoryItemRepository inventoryItemRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer createNutritionPlan(NutritionPlanCreateRequestDTO request) {
        // 1. Lấy thông tin người gọi (userId, roleName) từ SecurityContextHolder
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Người dùng chưa được xác thực.");
        }

        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng: " + username));

        String roleName = currentUser.getRoleName();

        // 2. Dùng horseId từ Request để tìm Horse trong DB. Nếu không thấy, throw ResourceNotFoundException
        Horse horse = horseRepository.findById(request.getHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Horse not found with id: " + request.getHorseId()));

        // 3. Kiểm tra BR_03:
        // - Nếu Horse.getIsLocked() == true VÀ roleName KHÔNG PHẢI LÀ "Veterinarian":
        //   Lập tức Throw AccessDeniedException (HTTP 403) với message:
        //   "Ngựa đang bị khóa y tế. Chỉ Bác sĩ Thú y mới được phép thiết lập dinh dưỡng lúc này."
        if (Boolean.TRUE.equals(horse.getIsLocked()) && !"Veterinarian".equalsIgnoreCase(roleName)) {
            throw new AccessDeniedException("Ngựa đang bị khóa y tế. Chỉ Bác sĩ Thú y mới được phép thiết lập dinh dưỡng lúc này.");
        }

        // - Nếu Horse.getIsLocked() == false VÀ roleName LÀ "Veterinarian":
        //   Throw AccessDeniedException (HTTP 403) với message:
        //   "Ngựa khỏe mạnh. Quyền lên thực đơn thuộc về Huấn luyện viên (Trainer)." (Chặn chéo bảo vệ dữ liệu).
        if (!Boolean.TRUE.equals(horse.getIsLocked()) && "Veterinarian".equalsIgnoreCase(roleName)) {
            throw new AccessDeniedException("Ngựa khỏe mạnh. Quyền lên thực đơn thuộc về Huấn luyện viên (Trainer).");
        }

        if (request.getEndDate() != null && request.getStartDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Ngày kết thúc (endDate) không được nhỏ hơn ngày bắt đầu (startDate).");
        }

        // 4. Tạo mới NutritionPlan (Lưu thông tin tổng quan)
        NutritionPlan plan = NutritionPlan.builder()
                .horse(horse)
                .createdBy(currentUser)
                .createdDate(LocalDateTime.now())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .notes(request.getNotes())
                .build();

        NutritionPlan savedPlan = nutritionPlanRepository.save(plan);

        // 5. Tạo danh sách NutritionPlanDetail từ DTO, link với NutritionPlan vừa tạo và gọi saveAll()
        List<NutritionPlanDetail> details = new ArrayList<>();
        if (request.getDetails() != null) {
            for (NutritionPlanDetailCreateDTO detailDTO : request.getDetails()) {
                InventoryItem item = inventoryItemRepository.findById(detailDTO.getInventoryItemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found with id: " + detailDTO.getInventoryItemId()));

                NutritionPlanDetail detail = NutritionPlanDetail.builder()
                        .nutritionPlan(savedPlan)
                        .inventoryItem(item)
                        .mealType(detailDTO.getMealType().toUpperCase())
                        .quantity(detailDTO.getQuantity())
                        .build();

                details.add(detail);
            }
            nutritionPlanDetailRepository.saveAll(details);
        }

        // 6. Trả về planId
        return savedPlan.getPlanId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NutritionTodayItemDTO> getTodayNutritionPlan(Integer horseId) {
        // Kiểm tra xem ngựa có tồn tại không
        if (!horseRepository.existsById(horseId)) {
            throw new ResourceNotFoundException("Horse not found with id: " + horseId);
        }

        // Truy vấn NutritionPlan mới nhất của ngựa thỏa mãn điều kiện: startDate <= TODAY và endDate >= TODAY
        LocalDate today = LocalDate.now();
        List<NutritionPlan> activePlans = nutritionPlanRepository.findActivePlansForHorseOnDate(horseId, today);

        if (activePlans.isEmpty()) {
            return Collections.emptyList();
        }

        NutritionPlan latestPlan = activePlans.get(0);
        return latestPlan.getDetails().stream().map(detail -> NutritionTodayItemDTO.builder()
                .mealType(detail.getMealType())
                .inventoryItemName(detail.getInventoryItem() != null ? detail.getInventoryItem().getItemName() : "Unknown")
                .quantity(detail.getQuantity())
                .inventoryItemId(detail.getInventoryItem() != null ? detail.getInventoryItem().getItemId() : null)
                .build()
        ).collect(Collectors.toList());
    }
}