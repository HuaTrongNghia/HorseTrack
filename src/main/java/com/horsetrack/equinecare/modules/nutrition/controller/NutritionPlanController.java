package com.horsetrack.equinecare.modules.nutrition.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionPlanCreateRequestDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionTodayItemDTO;
import com.horsetrack.equinecare.modules.nutrition.service.NutritionPlanService;
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
public class NutritionPlanController {

    private final NutritionPlanService nutritionPlanService;

    /**
     * API 2: Khởi tạo/Cập nhật Thực đơn Dinh dưỡng (Cốt lõi BR_03)
     * Quyền: @PreAuthorize("hasAnyAuthority('Head Trainer', 'Veterinarian')")
     */
    @PostMapping("/nutrition-plans")
    @PreAuthorize("hasAnyAuthority('Head Trainer', 'Veterinarian')")
    public ResponseEntity<ApiResponse<Integer>> createNutritionPlan(
            @Valid @RequestBody NutritionPlanCreateRequestDTO request) {
        Integer planId = nutritionPlanService.createNutritionPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Khởi tạo thực đơn dinh dưỡng thành công", planId));
    }

    /**
     * API 3: Xem Thực đơn hôm nay của một con ngựa
     * Quyền: Bất kỳ nhân viên nào đã đăng nhập.
     */
    @GetMapping("/horses/{horseId}/nutrition-plan/today")
    public ResponseEntity<ApiResponse<List<NutritionTodayItemDTO>>> getTodayNutritionPlan(
            @PathVariable Integer horseId) {
        List<NutritionTodayItemDTO> response = nutritionPlanService.getTodayNutritionPlan(horseId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thực đơn hôm nay thành công", response));
    }
}
