package com.horsetrack.equinecare.modules.inventory.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.inventory.dto.InventoryItemDTO;
import com.horsetrack.equinecare.modules.inventory.dto.ReasonRequestDTO;
import com.horsetrack.equinecare.modules.inventory.dto.SupplyRequestCreateDTO;
import com.horsetrack.equinecare.modules.inventory.dto.SupplyRequestDTO;
import com.horsetrack.equinecare.modules.inventory.dto.SupplyRequestIssueDTO;
import com.horsetrack.equinecare.modules.inventory.service.SupplyRequestService;
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
@Tag(name = "Supply Requests (Groom & Warehouse Manager)", description = "Groom yêu cầu vật tư; nhân viên kho cấp, chuyển chờ hàng hoặc từ chối")
public class SupplyRequestController {

    private final SupplyRequestService supplyRequestService;

    // ==========================================
    // GROOM
    // ==========================================

    @PostMapping("/supply-requests")
    @PreAuthorize("hasAuthority('Groom')")
    @Operation(summary = "Groom gửi yêu cầu vật tư", description = "Chỉ vật tư thông thường (không phải MEDICINE/VACCINE). Khu chuồng lấy từ hồ sơ Groom.")
    public ResponseEntity<ApiResponse<SupplyRequestDTO>> createRequest(@Valid @RequestBody SupplyRequestCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã gửi yêu cầu vật tư", supplyRequestService.createRequest(request)));
    }

    @GetMapping("/supply-requests/items")
    @PreAuthorize("hasAuthority('Groom')")
    @Operation(summary = "Danh mục vật tư Groom được yêu cầu", description = "Vật tư thật trong kho (trừ MEDICINE/VACCINE) kèm khả dụng; dùng cho ô chọn vật tư khi gửi yêu cầu.")
    public ResponseEntity<ApiResponse<List<InventoryItemDTO>>> getRequestableItems() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh mục vật tư thành công", supplyRequestService.getRequestableItems()));
    }

    @GetMapping("/supply-requests/my")
    @PreAuthorize("hasAuthority('Groom')")
    @Operation(summary = "Groom xem các yêu cầu của mình và trạng thái")
    public ResponseEntity<ApiResponse<List<SupplyRequestDTO>>> getMyRequests() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách yêu cầu vật tư thành công", supplyRequestService.getMyRequests()));
    }

    @PutMapping("/supply-requests/{requisitionId}/cancel")
    @PreAuthorize("hasAuthority('Groom')")
    @Operation(summary = "Groom hủy yêu cầu của mình", description = "Chỉ hủy được khi kho chưa cấp, chưa từ chối.")
    public ResponseEntity<ApiResponse<SupplyRequestDTO>> cancelMyRequest(@PathVariable Integer requisitionId) {
        return ResponseEntity.ok(ApiResponse.ok("Đã hủy yêu cầu vật tư", supplyRequestService.cancelMyRequest(requisitionId)));
    }

    // ==========================================
    // NHÂN VIÊN KHO
    // ==========================================

    @GetMapping("/inventory/supply-requests")
    @PreAuthorize("hasAuthority('Warehouse Manager')")
    @Operation(summary = "Danh sách yêu cầu vật tư của Groom", description = "Lọc theo status (SUBMITTED, CHECKING_STOCK, WAITING_RESTOCK, ISSUED, REJECTED, CANCELLED) và barnZone.")
    public ResponseEntity<ApiResponse<List<SupplyRequestDTO>>> getRequests(@RequestParam(required = false) String status,
                                                                           @RequestParam(required = false) String barnZone) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách yêu cầu vật tư thành công", supplyRequestService.getRequests(status, barnZone)));
    }

    @PutMapping("/inventory/supply-requests/{requisitionId}/issue")
    @PreAuthorize("hasAuthority('Warehouse Manager')")
    @Operation(summary = "Cấp vật tư", description = "Xuất kho và chuyển yêu cầu sang ISSUED. Body có thể bỏ trống để cấp đủ số lượng.")
    public ResponseEntity<ApiResponse<SupplyRequestDTO>> issue(@PathVariable Integer requisitionId,
                                                               @Valid @RequestBody(required = false) SupplyRequestIssueDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cấp vật tư cho Groom", supplyRequestService.issue(requisitionId, request)));
    }

    @PutMapping("/inventory/supply-requests/{requisitionId}/wait-restock")
    @PreAuthorize("hasAuthority('Warehouse Manager')")
    @Operation(summary = "Chuyển sang chờ hàng", description = "Dùng khi kho không đủ hàng. Tạo đơn mua purpose=REQUISITION cũng tự chuyển trạng thái này.")
    public ResponseEntity<ApiResponse<SupplyRequestDTO>> markWaitingRestock(@PathVariable Integer requisitionId) {
        return ResponseEntity.ok(ApiResponse.ok("Đã chuyển yêu cầu sang chờ hàng", supplyRequestService.markWaitingRestock(requisitionId)));
    }

    @PutMapping("/inventory/supply-requests/{requisitionId}/reject")
    @PreAuthorize("hasAuthority('Warehouse Manager')")
    @Operation(summary = "Từ chối yêu cầu vật tư", description = "Bắt buộc lý do.")
    public ResponseEntity<ApiResponse<SupplyRequestDTO>> reject(@PathVariable Integer requisitionId,
                                                                @Valid @RequestBody ReasonRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã từ chối yêu cầu vật tư", supplyRequestService.reject(requisitionId, request)));
    }
}
