package com.horsetrack.equinecare.modules.nutrition.service.impl;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.horse.service.HorseAccessGuard;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryItemRepository;
import com.horsetrack.equinecare.modules.nutrition.dto.DietChangeDecisionDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.DietChangeRequestCreateDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.DietChangeResponseDTO;
import com.horsetrack.equinecare.modules.nutrition.entity.DietChangeRequest;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlan;
import com.horsetrack.equinecare.modules.nutrition.repository.DietChangeRequestRepository;
import com.horsetrack.equinecare.modules.nutrition.repository.NutritionPlanRepository;
import com.horsetrack.equinecare.modules.nutrition.service.DietChangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DietChangeServiceImpl implements DietChangeService {

    private final DietChangeRequestRepository requestRepository;
    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final InventoryItemRepository itemRepository;
    private final NutritionPlanRepository nutritionPlanRepository;
    private final HorseAccessGuard horseAccessGuard;

    private User getLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Người dùng chưa được xác thực.");
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DietChangeResponseDTO createDietRequest(DietChangeRequestCreateDTO dto) {
        User user = getLoggedInUser();
        Horse horse = horseRepository.findById(dto.getHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + dto.getHorseId()));
        horseAccessGuard.checkAssigned(horse);

        InventoryItem item = null;
        if (dto.getItemId() != null) {
            item = itemRepository.findById(dto.getItemId()).orElse(null);
        }

        DietChangeRequest req = DietChangeRequest.builder()
                .horse(horse)
                .requestedBy(user)
                .item(item)
                .requestDetail(dto.getItem().trim())
                .reason(dto.getReason().trim())
                .requestedAt(LocalDateTime.now())
                .status("PENDING")
                .build();

        DietChangeRequest saved = requestRepository.save(req);
        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DietChangeResponseDTO> getDietRequests(Integer horseId, String status) {
        List<DietChangeRequest> list;
        if (horseId != null && StringUtils.hasText(status)) {
            list = requestRepository.findByHorse_HorseIdAndStatusOrderByRequestedAtDesc(horseId, status.trim().toUpperCase());
        } else if (horseId != null) {
            list = requestRepository.findByHorse_HorseIdOrderByRequestedAtDesc(horseId);
        } else if (StringUtils.hasText(status)) {
            list = requestRepository.findByStatusOrderByRequestedAtDesc(status.trim().toUpperCase());
        } else {
            list = requestRepository.findAllByOrderByRequestedAtDesc();
        }
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DietChangeResponseDTO getDietRequestDetail(Integer requestId) {
        DietChangeRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thức ăn đặc biệt ID: " + requestId));
        return toDTO(req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DietChangeResponseDTO decideDietRequest(Integer requestId, DietChangeDecisionDTO decision) {
        User user = getLoggedInUser();
        DietChangeRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thức ăn đặc biệt ID: " + requestId));

        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new IllegalStateException("Yêu cầu này đã được xử lý (trạng thái: " + req.getStatus() + ").");
        }

        boolean approve = Boolean.TRUE.equals(decision.getApprove());
        if (!approve) {
            if (!StringUtils.hasText(decision.getNote()) || decision.getNote().trim().length() < 10) {
                throw new IllegalArgumentException("Lý do từ chối phải có ít nhất 10 ký tự.");
            }
        }

        req.setDecidedBy(user);
        req.setDecidedAt(LocalDateTime.now());
        req.setStatus(approve ? "APPROVED" : "REJECTED");
        req.setVetNote(StringUtils.hasText(decision.getNote()) ? decision.getNote().trim() : null);

        if (approve && req.getHorse() != null) {
            LocalDate today = LocalDate.now();
            List<NutritionPlan> plans = nutritionPlanRepository.findActivePlansForHorseOnDate(req.getHorse().getHorseId(), today);
            if (!plans.isEmpty()) {
                NutritionPlan plan = plans.get(0);
                String currentSpecial = plan.getSpecialInstructions();
                String newSpecial = (!StringUtils.hasText(currentSpecial))
                        ? req.getRequestDetail()
                        : currentSpecial + "; " + req.getRequestDetail();
                plan.setSpecialInstructions(newSpecial);
                nutritionPlanRepository.save(plan);
                req.setNutritionPlanId(plan.getPlanId());
            }
        }

        DietChangeRequest saved = requestRepository.save(req);
        return toDTO(saved);
    }

    private DietChangeResponseDTO toDTO(DietChangeRequest r) {
        return DietChangeResponseDTO.builder()
                .requestId(r.getRequestId())
                .requestCode(r.getRequestCode() != null ? r.getRequestCode() : "FR-" + r.getRequestId())
                .horseId(r.getHorse() != null ? r.getHorse().getHorseId() : null)
                .horseName(r.getHorse() != null ? r.getHorse().getHorseName() : null)
                .requestedById(r.getRequestedBy() != null ? r.getRequestedBy().getUserId() : null)
                .requestedByName(r.getRequestedBy() != null ? r.getRequestedBy().getFullName() : null)
                .itemId(r.getItem() != null ? r.getItem().getItemId() : null)
                .itemCode(r.getItem() != null ? r.getItem().getItemCode() : null)
                .itemName(r.getItem() != null ? r.getItem().getItemName() : null)
                .item(r.getRequestDetail())
                .requestDetail(r.getRequestDetail())
                .reason(r.getReason())
                .requestedAt(r.getRequestedAt())
                .status(r.getStatus())
                .decidedById(r.getDecidedBy() != null ? r.getDecidedBy().getUserId() : null)
                .decidedByName(r.getDecidedBy() != null ? r.getDecidedBy().getFullName() : null)
                .decidedAt(r.getDecidedAt())
                .vetNote(r.getVetNote())
                .nutritionPlanId(r.getNutritionPlanId())
                .build();
    }
}
