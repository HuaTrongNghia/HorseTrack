package com.horsetrack.equinecare.modules.nutrition.service.impl;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.inventory.dto.InventoryItemDTO;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryItemRepository;
import com.horsetrack.equinecare.modules.inventory.service.InventoryMapper;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionPlanCreateRequestDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionPlanDetailCreateDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionPlanResponseDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionTodayItemDTO;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlan;
import com.horsetrack.equinecare.modules.nutrition.entity.NutritionPlanDetail;
import com.horsetrack.equinecare.modules.nutrition.repository.DietChangeRequestRepository;
import com.horsetrack.equinecare.modules.nutrition.repository.NutritionPlanDetailRepository;
import com.horsetrack.equinecare.modules.nutrition.repository.NutritionPlanRepository;
import com.horsetrack.equinecare.modules.nutrition.service.NutritionFeedTaskService;
import com.horsetrack.equinecare.modules.nutrition.service.NutritionPlanService;
import com.horsetrack.equinecare.modules.nutrition.service.NutritionScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NutritionPlanServiceImpl implements NutritionPlanService {

    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";
    public static final String CANCELLED = "CANCELLED";

    private final NutritionPlanRepository nutritionPlanRepository;
    private final NutritionPlanDetailRepository nutritionPlanDetailRepository;
    private final HorseRepository horseRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final DietChangeRequestRepository dietChangeRequestRepository;
    private final NutritionScope scope;
    private final NutritionFeedTaskService feedTaskService;
    private final AuditTrail auditTrail;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer createNutritionPlan(NutritionPlanCreateRequestDTO request) {
        User currentUser = scope.currentUser();
        Horse horse = horseRepository.findById(request.getHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + request.getHorseId()));
        // BR_03: ngựa khỏe → HLV phụ trách; ngựa đang khóa huấn luyện (bị thương) → bác sĩ thú y
        scope.checkCanCreate(currentUser, horse);
        if (horse.getStall() == null) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " chưa được duyệt và cấp chuồng, chưa thể lập khẩu phần.");
        }
        validateDates(request);
        // Khẩu phần mới không bắt đầu từ ngày đã qua (tránh sinh việc cho ăn đã trễ ngay khi tạo)
        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Ngày bắt đầu khẩu phần không được ở quá khứ (hôm nay là " + LocalDate.now() + ").");
        }

        String planType = Boolean.TRUE.equals(horse.getIsLocked()) ? "MEDICAL" : "STANDARD";
        NutritionPlan plan = NutritionPlan.builder()
                .horse(horse)
                .createdBy(currentUser)
                .createdDate(LocalDateTime.now())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .notes(request.getNotes())
                .status(ACTIVE)
                .planType(planType)
                .build();
        NutritionPlan savedPlan = nutritionPlanRepository.save(plan);
        savedPlan.getDetails().addAll(nutritionPlanDetailRepository.saveAll(buildDetails(savedPlan, request.getDetails())));

        // Mỗi ngựa chỉ có một khẩu phần áp dụng tại một thời điểm: khẩu phần mới thay thế khẩu phần đang áp dụng
        int replaced = replaceOverlapping(savedPlan);

        // Gửi cho nhân viên: tạo việc cho ăn hôm nay (các bữa chưa tới giờ) cho Groom của khu chuồng
        int tasks = feedTaskService.generateForPlan(savedPlan, LocalDate.now(), true);

        auditTrail.record("CREATE_NUTRITION_PLAN", "Nutrition_Plans", null,
                "Khẩu phần #" + savedPlan.getPlanId() + " (" + planType + ") cho ngựa " + horse.getHorseName()
                        + " " + request.getStartDate() + " → " + request.getEndDate()
                        + (replaced > 0 ? " | thay thế " + replaced + " khẩu phần cũ" : "") + " | gửi " + tasks + " việc cho ăn hôm nay");
        return savedPlan.getPlanId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NutritionTodayItemDTO> getTodayNutritionPlan(Integer horseId) {
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + horseId));
        scope.checkView(scope.currentUser(), horse);

        List<NutritionPlan> activePlans = nutritionPlanRepository.findActivePlansForHorseOnDate(horseId, LocalDate.now());
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
        User user = scope.currentUser();
        String st = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        List<NutritionPlan> plans;
        if (horseId != null) {
            Horse horse = horseRepository.findById(horseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + horseId));
            scope.checkView(user, horse);
            plans = st != null
                    ? nutritionPlanRepository.findByHorse_HorseIdAndStatusOrderByCreatedDateDesc(horseId, st)
                    : nutritionPlanRepository.findByHorse_HorseIdOrderByCreatedDateDesc(horseId);
        } else {
            plans = (st != null ? nutritionPlanRepository.findByStatusOrderByCreatedDateDesc(st) : nutritionPlanRepository.findAllByOrderByCreatedDateDesc())
                    .stream().filter(p -> scope.canView(user, p.getHorse())).collect(Collectors.toList());
        }
        return plans.stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public NutritionPlanResponseDTO getNutritionPlanDetail(Integer planId) {
        NutritionPlan plan = findPlan(planId);
        scope.checkView(scope.currentUser(), plan.getHorse());
        return toResponseDTO(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NutritionPlanResponseDTO updateNutritionPlan(Integer planId, NutritionPlanCreateRequestDTO request) {
        NutritionPlan plan = findPlan(planId);
        scope.checkCanManage(scope.currentUser(), plan);
        if (!ACTIVE.equals(plan.getStatus())) {
            throw new IllegalArgumentException("Chỉ sửa được khẩu phần đang áp dụng (hiện tại: " + plan.getStatus() + ").");
        }
        if (request.getHorseId() != null && !request.getHorseId().equals(plan.getHorse().getHorseId())) {
            throw new IllegalArgumentException("Không thể đổi ngựa của khẩu phần.");
        }
        validateDates(request);
        // Khẩu phần đang chạy giữ nguyên ngày bắt đầu cũ; đổi ngày bắt đầu thì không được lùi về quá khứ
        if (!request.getStartDate().equals(plan.getStartDate()) && request.getStartDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Ngày bắt đầu mới không được ở quá khứ (hôm nay là " + LocalDate.now() + ").");
        }

        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setNotes(request.getNotes());
        nutritionPlanDetailRepository.deleteAll(plan.getDetails());
        plan.getDetails().clear();
        nutritionPlanDetailRepository.flush();
        plan.getDetails().addAll(nutritionPlanDetailRepository.saveAll(buildDetails(plan, request.getDetails())));
        nutritionPlanRepository.save(plan);

        // Việc cho ăn chưa làm theo khẩu phần cũ bị hủy, gửi lại theo khẩu phần mới
        feedTaskService.skipOpenTasks(plan, "khẩu phần được cập nhật");
        replaceOverlapping(plan);
        feedTaskService.generateForPlan(plan, LocalDate.now(), true);
        auditTrail.record("UPDATE_NUTRITION_PLAN", "Nutrition_Plans", null,
                "Khẩu phần #" + plan.getPlanId() + " ngựa " + plan.getHorse().getHorseName() + " " + plan.getStartDate() + " → " + plan.getEndDate());
        return toResponseDTO(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelNutritionPlan(Integer planId) {
        NutritionPlan plan = findPlan(planId);
        scope.checkCanManage(scope.currentUser(), plan);
        if (!ACTIVE.equals(plan.getStatus())) {
            throw new IllegalArgumentException("Khẩu phần không còn áp dụng (hiện tại: " + plan.getStatus() + ").");
        }
        plan.setStatus(CANCELLED);
        nutritionPlanRepository.save(plan);
        int skipped = feedTaskService.skipOpenTasks(plan, "khẩu phần bị hủy");
        auditTrail.record("CANCEL_NUTRITION_PLAN", "Nutrition_Plans", "status=ACTIVE",
                "status=CANCELLED | khẩu phần #" + plan.getPlanId() + " ngựa " + plan.getHorse().getHorseName() + " | hủy " + skipped + " việc cho ăn");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNutritionPlan(Integer planId) {
        NutritionPlan plan = findPlan(planId);
        scope.checkCanManage(scope.currentUser(), plan);
        // Khẩu phần đã bắt đầu / đã có đề xuất gắn vào là dữ liệu lịch sử: chỉ được hủy, không xóa
        if (!plan.getStartDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Khẩu phần đã bắt đầu áp dụng, không thể xóa. Hãy dùng chức năng hủy (PUT /nutrition-plans/{id}/cancel).");
        }
        boolean referenced = dietChangeRequestRepository.findByHorse_HorseIdOrderByRequestedAtDesc(plan.getHorse().getHorseId()).stream()
                .anyMatch(r -> planId.equals(r.getNutritionPlanId()));
        if (referenced) {
            throw new IllegalArgumentException("Khẩu phần đã có đề xuất thức ăn được duyệt gắn vào, không thể xóa. Hãy dùng chức năng hủy.");
        }
        nutritionPlanRepository.delete(plan);
        auditTrail.record("DELETE_NUTRITION_PLAN", "Nutrition_Plans", "Khẩu phần #" + planId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int sendFeedTasks(Integer planId, LocalDate date) {
        NutritionPlan plan = findPlan(planId);
        scope.checkCanManage(scope.currentUser(), plan);
        LocalDate day = date != null ? date : LocalDate.now();
        if (day.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Không gửi việc cho ăn cho ngày đã qua.");
        }
        if (!ACTIVE.equals(plan.getStatus()) || day.isBefore(plan.getStartDate()) || day.isAfter(plan.getEndDate())) {
            throw new IllegalArgumentException("Ngày " + day + " nằm ngoài thời gian áp dụng của khẩu phần (" + plan.getStartDate() + " → " + plan.getEndDate()
                    + ") hoặc khẩu phần không còn áp dụng.");
        }
        return feedTaskService.generateForPlan(plan, day, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemDTO> getFeedItems() {
        return inventoryItemRepository.findAll().stream()
                .filter(com.horsetrack.equinecare.modules.inventory.service.ItemCategories::isFeedItem)
                .sorted((a, b) -> a.getItemName().compareToIgnoreCase(b.getItemName()))
                .map(item -> InventoryMapper.toItemDTO(item, null))
                .collect(Collectors.toList());
    }

    // ==========================================
    // HỖ TRỢ
    // ==========================================

    private NutritionPlan findPlan(Integer planId) {
        return nutritionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khẩu phần với ID: " + planId));
    }

    private static void validateDates(NutritionPlanCreateRequestDTO request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Ngày kết thúc (endDate) không được nhỏ hơn ngày bắt đầu (startDate).");
        }
        if (request.getEndDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Khẩu phần phải còn hiệu lực từ hôm nay trở đi (endDate không được ở quá khứ).");
        }
    }

    private List<NutritionPlanDetail> buildDetails(NutritionPlan plan, List<NutritionPlanDetailCreateDTO> dtos) {
        List<NutritionPlanDetail> details = new ArrayList<>();
        for (NutritionPlanDetailCreateDTO d : dtos) {
            InventoryItem item = inventoryItemRepository.findById(d.getInventoryItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vật tư với ID: " + d.getInventoryItemId()));
            // Khẩu phần chỉ gồm thức ăn / thức ăn bổ sung đường uống; thuốc đi theo phác đồ của bác sĩ
            if (!com.horsetrack.equinecare.modules.inventory.service.ItemCategories.isFeedItem(item)) {
                throw new IllegalArgumentException("'" + item.getItemName() + "' (nhóm " + item.getCategory()
                        + ") không phải thức ăn. Chọn món trong danh sách GET /nutrition-plans/feed-items; thuốc phải kê qua phác đồ điều trị.");
            }
            details.add(NutritionPlanDetail.builder()
                    .nutritionPlan(plan)
                    .inventoryItem(item)
                    .mealType(normalizeMeal(d.getMealType()))
                    .quantity(d.getQuantity())
                    .build());
        }
        return details;
    }

    /** DB chỉ nhận MORNING / NOON / EVENING */
    static String normalizeMeal(String meal) {
        String m = meal.trim().toUpperCase();
        return "AFTERNOON".equals(m) ? "NOON" : m;
    }

    /** Khẩu phần đang áp dụng khác của ngựa giao thời gian với khẩu phần này: kết thúc trước ngày bắt đầu, hoặc hủy nếu chưa bắt đầu */
    private int replaceOverlapping(NutritionPlan current) {
        int n = 0;
        for (NutritionPlan p : nutritionPlanRepository.findByHorse_HorseIdAndStatusOrderByCreatedDateDesc(current.getHorse().getHorseId(), ACTIVE)) {
            if (p.getPlanId().equals(current.getPlanId()) || p.getEndDate().isBefore(current.getStartDate()) || p.getStartDate().isAfter(current.getEndDate())) {
                continue;
            }
            if (p.getStartDate().isBefore(current.getStartDate())) {
                p.setEndDate(current.getStartDate().minusDays(1));
                p.setStatus(p.getEndDate().isBefore(LocalDate.now()) ? ENDED : ACTIVE);
            } else {
                p.setStatus(CANCELLED);
            }
            p.setNotes(appendNote(p.getNotes(), "Thay thế bởi khẩu phần #" + current.getPlanId()));
            nutritionPlanRepository.save(p);
            // Khẩu phần cũ vẫn áp dụng tới hết ngày trước khi khẩu phần mới bắt đầu
            feedTaskService.skipOpenTasks(p, "thay thế bởi khẩu phần #" + current.getPlanId(), current.getStartDate());
            n++;
        }
        return n;
    }

    private static String appendNote(String notes, String add) {
        return StringUtils.hasText(notes) ? notes + "\n" + add : add;
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
                .status(p.getStatus() != null ? p.getStatus() : ACTIVE)
                .notes(p.getNotes())
                .specialInstructions(p.getSpecialInstructions())
                .details(detailDTOs)
                .build();
    }
}
