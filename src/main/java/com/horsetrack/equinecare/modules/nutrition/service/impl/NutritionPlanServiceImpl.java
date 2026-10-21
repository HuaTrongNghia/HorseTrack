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
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionPlanResponseDTO;
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
    private final com.horsetrack.equinecare.modules.horse.service.HorseAccessGuard horseAccessGuard;
    private final UserRepository userRepository;
    private final InventoryItemRepository inventoryItemRepository;

    private User getLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Người dùng chưa được xác thực.");
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng: " + username));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer createNutritionPlan(NutritionPlanCreateRequestDTO request) {
        User currentUser = getLoggedInUser();
        String roleName = currentUser.getRoleName();

        Horse horse = horseRepository.findById(request.getHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Horse not found with id: " + request.getHorseId()));
        horseAccessGuard.checkAssigned(horse);

        if (Boolean.TRUE.equals(horse.getIsLocked()) && !"Veterinarian".equalsIgnoreCase(roleName)) {
            throw new AccessDeniedException("Ngựa đang bị khóa y tế. Chỉ Bác sĩ Thú y mới được phép thiết lập dinh dưỡng lúc này.");
        }
        if (!Boolean.TRUE.equals(horse.getIsLocked()) && "Veterinarian".equalsIgnoreCase(roleName)) {
            throw new AccessDeniedException("Ngựa khỏe mạnh. Quyền lên thực đơn thuộc về Huấn luyện viên (Trainer).");
        }

        if (request.getEndDate() != null && request.getStartDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Ngày kết thúc (endDate) không được nhỏ hơn ngày bắt đầu (startDate).");
        }

        NutritionPlan plan = NutritionPlan.builder()
                .horse(horse)
                .createdBy(currentUser)
                .createdDate(LocalDateTime.now())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .notes(request.getNotes())
                .status("ACTIVE")
                .planType(Boolean.TRUE.equals(horse.getIsLocked()) ? "MEDICAL" : "STANDARD")
                .build();

        NutritionPlan savedPlan = nutritionPlanRepository.save(plan);

        if (request.getDetails() != null) {
            List<NutritionPlanDetail> details = new ArrayList<>();
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

        return savedPlan.getPlanId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NutritionTodayItemDTO> getTodayNutritionPlan(Integer horseId) {
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Horse not found with id: " + horseId));
        horseAccessGuard.checkAssigned(horse);

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

    @Override
    @Transactional(readOnly = true)
    public List<NutritionPlanResponseDTO> getNutritionPlans(Integer horseId, String status) {
        List<NutritionPlan> plans;
        if (horseId != null && status != null) {
            plans = nutritionPlanRepository.findByHorse_HorseIdAndStatusOrderByCreatedDateDesc(horseId, status);
        } else if (horseId != null) {
            plans = nutritionPlanRepository.findByHorse_HorseIdOrderByCreatedDateDesc(horseId);
        } else if (status != null) {
            plans = nutritionPlanRepository.findByStatusOrderByCreatedDateDesc(status);
        } else {
            plans = nutritionPlanRepository.findAllByOrderByCreatedDateDesc();
        }
        return plans.stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public NutritionPlanResponseDTO getNutritionPlanDetail(Integer planId) {
        NutritionPlan plan = nutritionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực đơn với ID: " + planId));
        return toResponseDTO(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NutritionPlanResponseDTO updateNutritionPlan(Integer planId, NutritionPlanCreateRequestDTO request) {
        NutritionPlan plan = nutritionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực đơn với ID: " + planId));

        User currentUser = getLoggedInUser();
        String roleName = currentUser.getRoleName();
        Horse horse = plan.getHorse();
        horseAccessGuard.checkAssigned(horse);

        if (Boolean.TRUE.equals(horse.getIsLocked()) && !"Veterinarian".equalsIgnoreCase(roleName) && !"Club Manager".equalsIgnoreCase(roleName)) {
            throw new AccessDeniedException("Ngựa đang bị khóa y tế. Chỉ Bác sĩ Thú y mới được phép chỉnh sửa dinh dưỡng.");
        }

        if (request.getEndDate() != null && request.getStartDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Ngày kết thúc không được nhỏ hơn ngày bắt đầu.");
        }

        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setNotes(request.getNotes());

        nutritionPlanDetailRepository.deleteAll(plan.getDetails());
        plan.getDetails().clear();

        if (request.getDetails() != null) {
            List<NutritionPlanDetail> newDetails = new ArrayList<>();
            for (NutritionPlanDetailCreateDTO d : request.getDetails()) {
                InventoryItem item = inventoryItemRepository.findById(d.getInventoryItemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + d.getInventoryItemId()));
                NutritionPlanDetail detail = NutritionPlanDetail.builder()
                        .nutritionPlan(plan)
                        .inventoryItem(item)
                        .mealType(d.getMealType().toUpperCase())
                        .quantity(d.getQuantity())
                        .build();
                newDetails.add(detail);
            }
            nutritionPlanDetailRepository.saveAll(newDetails);
            plan.getDetails().addAll(newDetails);
        }

        nutritionPlanRepository.save(plan);
        return toResponseDTO(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelNutritionPlan(Integer planId) {
        NutritionPlan plan = nutritionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực đơn với ID: " + planId));
        plan.setStatus("CANCELLED");
        nutritionPlanRepository.save(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNutritionPlan(Integer planId) {
        NutritionPlan plan = nutritionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực đơn với ID: " + planId));
        nutritionPlanRepository.delete(plan);
    }

    private NutritionPlanResponseDTO toResponseDTO(NutritionPlan p) {
        List<NutritionPlanResponseDTO.NutritionPlanDetailDTO> detailDTOs = p.getDetails() == null ? Collections.emptyList()
                : p.getDetails().stream().map(d -> NutritionPlanResponseDTO.NutritionPlanDetailDTO.builder()
                .detailId(d.getDetailId())
                .inventoryItemId(d.getInventoryItem() != null ? d.getInventoryItem().getItemId() : null)
                .itemCode(d.getInventoryItem() != null ? d.getInventoryItem().getItemCode() : null)
                .itemName(d.getInventoryItem() != null ? d.getInventoryItem().getItemName() : null)
                .unit(d.getInventoryItem() != null ? d.getInventoryItem().getUnit() : null)
                .mealType(d.getMealType())
                .quantity(d.getQuantity())
                .build()).collect(Collectors.toList());

        return NutritionPlanResponseDTO.builder()
                .planId(p.getPlanId())
                .horseId(p.getHorse() != null ? p.getHorse().getHorseId() : null)
                .horseName(p.getHorse() != null ? p.getHorse().getHorseName() : null)
                .createdById(p.getCreatedBy() != null ? p.getCreatedBy().getUserId() : null)
                .createdByName(p.getCreatedBy() != null ? p.getCreatedBy().getFullName() : null)
                .createdDate(p.getCreatedDate())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .planType(p.getPlanType() != null ? p.getPlanType() : "STANDARD")
                .status(p.getStatus() != null ? p.getStatus() : "ACTIVE")
                .notes(p.getNotes())
                .specialInstructions(p.getSpecialInstructions())
                .details(detailDTOs)
                .build();
    }
}
