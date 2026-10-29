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
    private final com.horsetrack.equinecare.modules.nutrition.service.NutritionScope scope;
    private final com.horsetrack.equinecare.modules.nutrition.service.NutritionFeedTaskService feedTaskService;
    private final com.horsetrack.equinecare.modules.audit.service.AuditTrail auditTrail;
    private final com.horsetrack.equinecare.modules.notification.service.EmailService emailService;
    private final com.horsetrack.equinecare.modules.notification.service.AppNotificationService appNotifications;

    private User currentUserOrNull() {
        try {
            return getLoggedInUser();
        } catch (RuntimeException ex) {
            return null;
        }
    }

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
        // Trainer chỉ ngựa được phân công; Groom chỉ ngựa trong khu mình phụ trách
        horseAccessGuard.checkAssigned(horse);
        scope.checkGroomZone(user, horse);
        if (horse.getStall() == null) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " chưa ở CLB, không thể đề xuất thức ăn.");
        }

        // Ngựa không khóa: đề xuất gửi HLV phụ trách. HLV muốn đổi khẩu phần ngựa khỏe thì sửa trực tiếp, không gửi cho chính mình
        if (!locked(horse) && com.horsetrack.equinecare.modules.nutrition.service.NutritionScope.ROLE_TRAINER.equals(user.getRoleName())) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " không bị khóa huấn luyện: HLV phụ trách điều chỉnh trực tiếp khẩu phần (PUT /api/v1/nutrition-plans/{planId}), không cần gửi đề xuất.");
        }
        if (!locked(horse) && horse.getAssignedTrainer() == null) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " chưa được phân công HLV, chưa có người duyệt đề xuất thức ăn.");
        }

        InventoryItem item = null;
        if (dto.getItemId() != null) {
            item = itemRepository.findById(dto.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vật tư với ID: " + dto.getItemId()));
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
        notifyApprover(saved);
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
        User user = scope.currentUser();
        return list.stream().filter(r -> scope.canView(user, r.getHorse())).map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DietChangeResponseDTO getDietRequestDetail(Integer requestId) {
        DietChangeRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thức ăn đặc biệt ID: " + requestId));
        scope.checkView(scope.currentUser(), req.getHorse());
        return toDTO(req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DietChangeResponseDTO decideDietRequest(Integer requestId, DietChangeDecisionDTO decision) {
        User user = getLoggedInUser();
        DietChangeRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thức ăn đặc biệt ID: " + requestId));

        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new IllegalArgumentException("Yêu cầu này đã được xử lý (trạng thái: " + req.getStatus() + ").");
        }
        // BR_03: ngựa không khóa → HLV phụ trách ngựa duyệt; ngựa đang khóa huấn luyện → bác sĩ thú y duyệt
        if (!canDecide(user, req.getHorse())) {
            throw new AccessDeniedException(locked(req.getHorse())
                    ? "Ngựa " + req.getHorse().getHorseName() + " đang bị khóa huấn luyện: đề xuất thức ăn do Bác sĩ thú y xét duyệt."
                    : "Ngựa " + req.getHorse().getHorseName() + " không bị khóa huấn luyện: đề xuất thức ăn do HLV phụ trách ngựa xét duyệt.");
        }

        boolean approve = Boolean.TRUE.equals(decision.getApprove());
        if (!approve) {
            if (!StringUtils.hasText(decision.getNote())) {
                throw new IllegalArgumentException("Phải ghi lý do từ chối.");
            }
        }

        // Duyệt thì phải có khẩu phần đang áp dụng để bổ sung, nếu không đề xuất sẽ không có tác dụng
        List<NutritionPlan> activePlans = approve && req.getHorse() != null
                ? nutritionPlanRepository.findActivePlansForHorseOnDate(req.getHorse().getHorseId(), LocalDate.now()) : List.of();
        if (approve && activePlans.isEmpty()) {
            throw new IllegalArgumentException("Ngựa " + req.getHorse().getHorseName() + " chưa có khẩu phần đang áp dụng để bổ sung. Hãy lập khẩu phần trước khi duyệt.");
        }

        req.setDecidedBy(user);
        req.setDecidedAt(LocalDateTime.now());
        req.setStatus(approve ? "APPROVED" : "REJECTED");
        req.setVetNote(StringUtils.hasText(decision.getNote()) ? decision.getNote().trim() : null);

        if (approve) {
            {
                NutritionPlan plan = activePlans.get(0);
                String currentSpecial = plan.getSpecialInstructions();
                String newSpecial = (!StringUtils.hasText(currentSpecial))
                        ? req.getRequestDetail()
                        : currentSpecial + "; " + req.getRequestDetail();
                plan.setSpecialInstructions(newSpecial);
                nutritionPlanRepository.save(plan);
                req.setNutritionPlanId(plan.getPlanId());
                // Lưu ý đặc biệt hiển thị ngay trên các việc cho ăn chưa làm của Groom
                feedTaskService.refreshDietNote(plan);
            }
        }

        DietChangeRequest saved = requestRepository.save(req);
        auditTrail.record(approve ? "APPROVE_DIET_REQUEST" : "REJECT_DIET_REQUEST", "Diet_Change_Requests", "status=PENDING",
                "status=" + saved.getStatus() + " | ngựa " + (req.getHorse() != null ? req.getHorse().getHorseName() : "?") + " | " + req.getRequestDetail());
        return toDTO(saved);
    }

    private static boolean locked(Horse horse) {
        return horse != null && Boolean.TRUE.equals(horse.getIsLocked());
    }

    private static String approverRole(Horse horse) {
        return locked(horse) ? com.horsetrack.equinecare.modules.nutrition.service.NutritionScope.ROLE_VET
                : com.horsetrack.equinecare.modules.nutrition.service.NutritionScope.ROLE_TRAINER;
    }

    private static boolean canDecide(User user, Horse horse) {
        if (horse == null || user == null || !approverRole(horse).equals(user.getRoleName())) {
            return false;
        }
        return locked(horse) || (horse.getAssignedTrainer() != null && horse.getAssignedTrainer().getUserId().equals(user.getUserId()));
    }

    // Báo người duyệt: HLV phụ trách (ngựa không khóa) hoặc các bác sĩ thú y (ngựa đang khóa)
    private void notifyApprover(DietChangeRequest r) {
        Horse horse = r.getHorse();
        List<User> to = locked(horse)
                ? userRepository.findByRoleNameInAndIsActiveTrue(List.of(com.horsetrack.equinecare.modules.nutrition.service.NutritionScope.ROLE_VET))
                : (horse.getAssignedTrainer() != null ? List.of(horse.getAssignedTrainer()) : List.<User>of());
        String esc = org.springframework.web.util.HtmlUtils.htmlEscape(horse.getHorseName());
        String link = locked(horse) ? "/vet" : "/trainer";
        String title = "Đề nghị điều chỉnh khẩu phần — ngựa " + horse.getHorseName();
        String plain = r.getRequestedBy().getFullName() + " đề nghị: " + r.getRequestDetail()
                + ". Lý do: " + r.getReason();
        for (User u : to) {
            appNotifications.push(u, "DIET_REQUEST", title, plain, link);
            if (!StringUtils.hasText(u.getEmail())) {
                continue;
            }
            try {
                emailService.sendAsync("DIET_REQUEST", u.getEmail(), "[HorseTrack] " + title,
                        "<p>Xin chào <b>" + org.springframework.web.util.HtmlUtils.htmlEscape(u.getFullName()) + "</b>,</p>"
                                + "<p><b>" + org.springframework.web.util.HtmlUtils.htmlEscape(r.getRequestedBy().getFullName()) + "</b> đề nghị điều chỉnh khẩu phần cho ngựa <b>" + esc + "</b>: "
                                + org.springframework.web.util.HtmlUtils.htmlEscape(r.getRequestDetail()) + ".</p><p>Lý do: " + org.springframework.web.util.HtmlUtils.htmlEscape(r.getReason()) + "</p>"
                                + "<p>Vui lòng xét duyệt trên HorseTrack.</p>");
            } catch (Exception ex) {
                // Không làm hỏng việc gửi đề xuất nếu email lỗi
            }
        }
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
                .approverRole(approverRole(r.getHorse()))
                .approverName(locked(r.getHorse()) ? "Bác sĩ thú y"
                        : r.getHorse() != null && r.getHorse().getAssignedTrainer() != null ? r.getHorse().getAssignedTrainer().getFullName() : null)
                .canDecide("PENDING".equals(r.getStatus()) && canDecide(currentUserOrNull(), r.getHorse()))
                .build();
    }
}
