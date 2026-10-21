package com.horsetrack.equinecare.modules.nutrition.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.inventory.dto.InventoryItemDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionPlanCreateRequestDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionPlanResponseDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionTodayItemDTO;
import com.horsetrack.equinecare.modules.nutrition.service.NutritionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Nutrition Plans", description = "Quản lý thực đơn / kế hoạch dinh dưỡng của ngựa (BR_03)")
public class NutritionPlanController {

    private final NutritionPlanService nutritionPlanService;

    /**
     * Khởi tạo Thực đơn Dinh dưỡng (BR_03)
     */
    @PostMapping("/nutrition-plans")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Veterinarian', 'Club Manager')")
    @Operation(summary = "Tạo thực đơn dinh dưỡng mới")
    public ResponseEntity<ApiResponse<Integer>> createNutritionPlan(
            @Valid @RequestBody NutritionPlanCreateRequestDTO request) {
        Integer planId = nutritionPlanService.createNutritionPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Khởi tạo thực đơn dinh dưỡng thành công", planId));
    }

    /**
     * Xem danh sách thực đơn dinh dưỡng
     */
    @GetMapping("/nutrition-plans")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Veterinarian', 'Groom', 'Club Manager', 'Horse Owner')")
    @Operation(summary = "Xem danh sách thực đơn (lọc theo horseId, status)")
    public ResponseEntity<ApiResponse<List<NutritionPlanResponseDTO>>> getNutritionPlans(
            @RequestParam(required = false) Integer horseId,
            @RequestParam(required = false) String status) {
        List<NutritionPlanResponseDTO> plans = nutritionPlanService.getNutritionPlans(horseId, status);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách thực đơn thành công", plans));
    }

    @GetMapping("/nutrition-plans/feed-items")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Veterinarian', 'Groom', 'Club Manager')")
    @Operation(summary = "Danh sách thức ăn / phụ gia trong kho để lên thực đơn")
    public ResponseEntity<ApiResponse<List<InventoryItemDTO>>> getFeedItems() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách thức ăn thành công", nutritionPlanService.getFeedItems()));
    }

    /**
     * Xem chi tiết một thực đơn
     */
    @GetMapping("/nutrition-plans/{planId}")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Veterinarian', 'Groom', 'Club Manager', 'Horse Owner')")
    @Operation(summary = "Xem chi tiết thực đơn theo ID")
    public ResponseEntity<ApiResponse<NutritionPlanResponseDTO>> getNutritionPlanDetail(
            @PathVariable Integer planId) {
        NutritionPlanResponseDTO plan = nutritionPlanService.getNutritionPlanDetail(planId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết thực đơn thành công", plan));
    }

    /**
     * Sửa thực đơn dinh dưỡng
     */
    @PutMapping("/nutrition-plans/{planId}")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Veterinarian', 'Club Manager')")
    @Operation(summary = "Chỉnh sửa thực đơn dinh dưỡng")
    public ResponseEntity<ApiResponse<NutritionPlanResponseDTO>> updateNutritionPlan(
            @PathVariable Integer planId,
            @Valid @RequestBody NutritionPlanCreateRequestDTO request) {
        NutritionPlanResponseDTO updated = nutritionPlanService.updateNutritionPlan(planId, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thực đơn dinh dưỡng thành công", updated));
    }

    /**
     * Hủy thực đơn dinh dưỡng (đổi status = CANCELLED)
     */
    @PutMapping("/nutrition-plans/{planId}/cancel")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Veterinarian', 'Club Manager')")
    @Operation(summary = "Hủy thực đơn dinh dưỡng")
    public ResponseEntity<ApiResponse<Void>> cancelNutritionPlan(@PathVariable Integer planId) {
        nutritionPlanService.cancelNutritionPlan(planId);
        return ResponseEntity.ok(ApiResponse.ok("Đã hủy thực đơn dinh dưỡng thành công", null));
    }

    /**
     * Xóa thực đơn dinh dưỡng
     */
    @DeleteMapping("/nutrition-plans/{planId}")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Veterinarian', 'Club Manager')")
    @Operation(summary = "Xóa thực đơn dinh dưỡng")
    public ResponseEntity<ApiResponse<Void>> deleteNutritionPlan(@PathVariable Integer planId) {
        nutritionPlanService.deleteNutritionPlan(planId);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa thực đơn dinh dưỡng thành công", null));
    }

    /**
     * Xem Thực đơn hôm nay của một con ngựa
     */
    @GetMapping("/horses/{horseId}/nutrition-plan/today")
    @Operation(summary = "Xem thực đơn hôm nay của ngựa")
    public ResponseEntity<ApiResponse<List<NutritionTodayItemDTO>>> getTodayNutritionPlan(
            @PathVariable Integer horseId) {
        List<NutritionTodayItemDTO> response = nutritionPlanService.getTodayNutritionPlan(horseId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thực đơn hôm nay thành công", response));
    }
}
