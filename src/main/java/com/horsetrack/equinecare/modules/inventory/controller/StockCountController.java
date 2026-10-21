package com.horsetrack.equinecare.modules.inventory.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.inventory.dto.StockCountDTO;
import com.horsetrack.equinecare.modules.inventory.dto.StockCountRequestDTO;
import com.horsetrack.equinecare.modules.inventory.service.StockCountService;
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
@RequestMapping("/api/v1/inventory/stock-counts")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Warehouse Manager')")
@Tag(name = "Inventory - Monthly Stock Count (Warehouse Manager)", description = "Kiểm kê hàng tháng: nhập số đếm thực tế, so với hệ thống, chốt phiếu để điều chỉnh tồn kho")
public class StockCountController {

    private final StockCountService stockCountService;

    @GetMapping
    @Operation(summary = "Danh sách phiếu kiểm kê", description = "Mới nhất trước, kèm số dòng có chênh lệch.")
    public ResponseEntity<ApiResponse<List<StockCountDTO>>> getCounts() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách phiếu kiểm kê thành công", stockCountService.getCounts()));
    }

    @GetMapping("/{countId}")
    @Operation(summary = "Chi tiết phiếu kiểm kê", description = "Số hệ thống, số đếm thực tế, chênh lệch và lý do của từng vật tư.")
    public ResponseEntity<ApiResponse<StockCountDTO>> getCountDetail(@PathVariable Integer countId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết phiếu kiểm kê thành công", stockCountService.getCountDetail(countId)));
    }

    @PostMapping
    @Operation(summary = "Tạo phiếu kiểm kê (DRAFT)", description = "Mỗi tháng một phiếu. Số hệ thống được chụp tại lúc nhập.")
    public ResponseEntity<ApiResponse<StockCountDTO>> createCount(@Valid @RequestBody StockCountRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo phiếu kiểm kê thành công", stockCountService.createCount(request)));
    }

    @PutMapping("/{countId}")
    @Operation(summary = "Cập nhật phiếu kiểm kê", description = "Chỉ khi còn DRAFT. Gửi đầy đủ danh sách dòng; dòng không gửi sẽ bị bỏ.")
    public ResponseEntity<ApiResponse<StockCountDTO>> updateCount(@PathVariable Integer countId,
                                                                  @Valid @RequestBody StockCountRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật phiếu kiểm kê thành công", stockCountService.updateCount(countId, request)));
    }

    @PutMapping("/{countId}/complete")
    @Operation(summary = "Chốt phiếu kiểm kê", description = "Điều chỉnh tồn kho về đúng số đếm (ADJUST, ref STOCK_COUNT). Dòng chênh lệch phải có lý do.")
    public ResponseEntity<ApiResponse<StockCountDTO>> completeCount(@PathVariable Integer countId) {
        return ResponseEntity.ok(ApiResponse.ok("Đã chốt phiếu kiểm kê và điều chỉnh tồn kho", stockCountService.completeCount(countId)));
    }
}
