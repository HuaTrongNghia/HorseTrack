package com.horsetrack.equinecare.modules.nutrition.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.nutrition.dto.NutritionStockRequestDTO;
import com.horsetrack.equinecare.modules.nutrition.service.NutritionStockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Màn "Thực đơn dinh dưỡng" của kho: duyệt và cấp thức ăn theo ngày cho thực đơn của HLV / bác sĩ */
@RestController
@RequestMapping("/api/v1/inventory/nutrition-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Warehouse Manager')")
@Tag(name = "Inventory - Nutrition Stock (Warehouse Manager)", description = "Phiếu cấp thức ăn theo ngày cho thực đơn dinh dưỡng")
public class NutritionStockController {

    private final NutritionStockService stockService;

    @GetMapping
    @Operation(summary = "Phiếu cấp thức ăn theo thực đơn", description = "tab: pending (chờ cấp phát, mặc định), issued (đã cấp phát), cancelled (đã hủy / thay đổi), all. "
            + "Mỗi phần tử là một thực đơn trong một ngày, kèm các món, số lượng cần cấp và khả dụng. date=yyyy-MM-dd để lọc theo ngày.")
    public ResponseEntity<ApiResponse<List<NutritionStockRequestDTO>>> list(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy phiếu cấp thức ăn thành công", stockService.list(tab, date)));
    }

    @PutMapping("/{planId}/{date}/issue")
    @Operation(summary = "Cấp thức ăn một ngày của thực đơn", description = "Trừ kho mọi món chờ cấp của ngày đó, rồi giao việc cho ăn ngày đó cho Groom. Không đủ hàng → 400 (không trừ món nào).")
    public ResponseEntity<ApiResponse<NutritionStockRequestDTO>> issue(
            @PathVariable Integer planId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cấp thức ăn ngày " + date + " cho thực đơn #" + planId, stockService.issueDay(planId, date)));
    }
}
