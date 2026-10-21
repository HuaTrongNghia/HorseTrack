package com.horsetrack.equinecare.modules.inventory.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.inventory.dto.*;
import com.horsetrack.equinecare.modules.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Warehouse Manager')")
@Tag(name = "Inventory (Warehouse Manager)", description = "Quản lý kho: danh mục vật tư, tồn kho, nhập/xuất/điều chỉnh, mức an toàn, nhật ký kho")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/items")
    @Operation(summary = "Danh sách tồn kho", description = "Đang có, đang giữ, khả dụng và cờ cảnh báo dưới mức an toàn. Lọc theo category, keyword (tên hoặc mã).")
    public ResponseEntity<ApiResponse<List<InventoryItemDTO>>> getItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách tồn kho thành công", inventoryService.getItems(category, keyword)));
    }

    @GetMapping("/items/{itemId}")
    @Operation(summary = "Chi tiết vật tư", description = "Thông tin vật tư kèm danh sách lô hàng.")
    public ResponseEntity<ApiResponse<InventoryItemDTO>> getItemDetail(@PathVariable Integer itemId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết vật tư thành công", inventoryService.getItemDetail(itemId)));
    }

    @PostMapping("/items")
    @Operation(summary = "Thêm vật tư mới", description = "Tồn kho ban đầu = 0; dùng nhập kho (RECEIVE) để đưa hàng vào.")
    public ResponseEntity<ApiResponse<InventoryItemDTO>> createItem(@Valid @RequestBody InventoryItemRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Thêm vật tư thành công", inventoryService.createItem(request)));
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Cập nhật thông tin vật tư", description = "Không sửa tồn kho ở đây; tồn kho chỉ thay đổi qua stock-movements.")
    public ResponseEntity<ApiResponse<InventoryItemDTO>> updateItem(
            @PathVariable Integer itemId,
            @Valid @RequestBody InventoryItemRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật vật tư thành công", inventoryService.updateItem(itemId, request)));
    }

    @PutMapping("/items/{itemId}/safety-level")
    @Operation(summary = "Đặt mức tồn kho an toàn (BR_08)")
    public ResponseEntity<ApiResponse<InventoryItemDTO>> updateSafetyLevel(
            @PathVariable Integer itemId,
            @Valid @RequestBody SafetyLevelRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật mức tồn kho an toàn thành công", inventoryService.updateSafetyLevel(itemId, request)));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Cảnh báo tồn kho thấp (BR_08)", description = "Vật tư có khả dụng (đang có - đang giữ) thấp hơn mức an toàn.")
    public ResponseEntity<ApiResponse<List<InventoryItemDTO>>> getLowStockItems() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách vật tư dưới mức an toàn thành công", inventoryService.getLowStockItems()));
    }

    @PostMapping("/stock-movements")
    @Operation(summary = "Nhập / xuất / điều chỉnh / hủy hàng",
            description = "RECEIVE (lotNo tùy chọn, để trống thì tự sinh), ISSUE, DISPOSE (bắt buộc note), ADJUST (quantity âm để giảm, bắt buộc note). Mỗi lần ghi một dòng nhật ký kho.")
    public ResponseEntity<ApiResponse<StockMovementResponseDTO>> recordMovement(@Valid @RequestBody StockMovementRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Ghi nhận phát sinh kho thành công", inventoryService.recordMovement(request)));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Nhật ký kho", description = "Lịch sử mọi phát sinh kho, mới nhất trước. Lọc theo itemId, type, fromDate, toDate (yyyy-MM-dd).")
    public ResponseEntity<ApiResponse<Page<StockTransactionDTO>>> getTransactions(
            @RequestParam(required = false) Integer itemId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy nhật ký kho thành công",
                inventoryService.getTransactions(itemId, type, fromDate, toDate, page, size)));
    }
}
