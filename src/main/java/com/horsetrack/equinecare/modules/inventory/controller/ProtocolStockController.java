package com.horsetrack.equinecare.modules.inventory.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.inventory.dto.StockCheckRequestDTO;
import com.horsetrack.equinecare.modules.inventory.service.ProtocolStockService;
import com.horsetrack.equinecare.modules.medical.dto.TreatmentProtocolDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory/protocols")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Warehouse Manager')")
@Tag(name = "Inventory - Protocol Medicines (Warehouse Manager)", description = "Kiểm kho, giữ hàng và cấp thuốc cho phác đồ điều trị (BR_07). Kho không có quyền quyết định điều trị.")
public class ProtocolStockController {

    private final ProtocolStockService protocolStockService;

    @GetMapping
    @Operation(summary = "Phác đồ chờ kho xử lý", description = "Mặc định: WAITING_STOCK_CHECK, WAITING_RESTOCK, REQUIRES_VET_REVIEW, VET_CONFIRMED. Truyền state để lọc một trạng thái.")
    public ResponseEntity<ApiResponse<List<TreatmentProtocolDTO>>> getQueue(@RequestParam(required = false) String state) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách phác đồ chờ kho thành công", protocolStockService.getQueue(state)));
    }

    @GetMapping("/{protocolId}")
    @Operation(summary = "Chi tiết thuốc của phác đồ", description = "Thuốc theo giai đoạn và kết quả kiểm kho gần nhất.")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> getDetail(@PathVariable Integer protocolId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết phác đồ thành công", protocolStockService.getDetail(protocolId)));
    }

    @PutMapping("/{protocolId}/stock-check")
    @Operation(summary = "Kiểm kho cho phác đồ",
            description = "Đủ hàng thì giữ hàng (STOCK_AVAILABLE). Thiếu thì giữ phần đang có, ghi số thiếu và ngày về (STOCK_SHORTAGE) để bác sĩ quyết định. Gọi lại khi hàng về để kiểm lại.")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> checkStock(@PathVariable Integer protocolId,
                                                                        @Valid @RequestBody(required = false) StockCheckRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã kiểm kho cho phác đồ", protocolStockService.checkStock(protocolId, request)));
    }

    @PutMapping("/{protocolId}/dispense")
    @Operation(summary = "Cấp thuốc", description = "Chỉ sau khi bác sĩ xác nhận (VET_CONFIRMED). Xuất phần đã giữ, phác đồ chuyển sang IN_TREATMENT.")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> dispense(@PathVariable Integer protocolId) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cấp thuốc cho phác đồ", protocolStockService.dispense(protocolId)));
    }
}
