package com.horsetrack.equinecare.modules.inventory.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.inventory.dto.*;
import com.horsetrack.equinecare.modules.inventory.service.PurchaseOrderService;
import com.horsetrack.equinecare.modules.inventory.service.SupplierService;
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
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Warehouse Manager')")
@Tag(name = "Inventory - Purchase Orders (Warehouse Manager)", description = "Nhà cung cấp và đơn mua hàng: tạo đơn, nhận hàng nhập kho, đổi ngày về, hủy đơn")
public class PurchaseOrderController {

    private final SupplierService supplierService;
    private final PurchaseOrderService purchaseOrderService;

    // ==========================================
    // NHÀ CUNG CẤP
    // ==========================================

    @GetMapping("/suppliers")
    @Operation(summary = "Danh sách nhà cung cấp", description = "activeOnly=true chỉ lấy nhà cung cấp còn hợp tác.")
    public ResponseEntity<ApiResponse<List<SupplierDTO>>> getSuppliers(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách nhà cung cấp thành công", supplierService.getSuppliers(activeOnly)));
    }

    @PostMapping("/suppliers")
    @Operation(summary = "Thêm nhà cung cấp")
    public ResponseEntity<ApiResponse<SupplierDTO>> createSupplier(@Valid @RequestBody SupplierRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Thêm nhà cung cấp thành công", supplierService.createSupplier(request)));
    }

    @PutMapping("/suppliers/{supplierId}")
    @Operation(summary = "Cập nhật nhà cung cấp", description = "Gửi isActive=false để ngừng hợp tác.")
    public ResponseEntity<ApiResponse<SupplierDTO>> updateSupplier(@PathVariable Integer supplierId,
                                                                   @Valid @RequestBody SupplierRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật nhà cung cấp thành công", supplierService.updateSupplier(supplierId, request)));
    }

    // ==========================================
    // ĐƠN MUA HÀNG
    // ==========================================

    @GetMapping("/purchase-orders")
    @Operation(summary = "Danh sách đơn mua hàng", description = "Lọc theo status (ORDERED, IN_TRANSIT, DELAYED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED) và supplierId.")
    public ResponseEntity<ApiResponse<List<PurchaseOrderDTO>>> getOrders(@RequestParam(required = false) String status,
                                                                         @RequestParam(required = false) Integer supplierId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách đơn mua hàng thành công", purchaseOrderService.getOrders(status, supplierId)));
    }

    @GetMapping("/purchase-orders/{orderId}")
    @Operation(summary = "Chi tiết đơn mua hàng")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> getOrderDetail(@PathVariable Integer orderId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết đơn mua hàng thành công", purchaseOrderService.getOrderDetail(orderId)));
    }

    @PostMapping("/purchase-orders")
    @Operation(summary = "Tạo đơn mua hàng", description = "purpose = RESTOCK (bổ sung kho) hoặc REQUISITION (kèm requisitionId của yêu cầu vật tư Groom).")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> createOrder(@Valid @RequestBody PurchaseOrderCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo đơn mua hàng thành công", purchaseOrderService.createOrder(request)));
    }

    @PutMapping("/purchase-orders/{orderId}/in-transit")
    @Operation(summary = "Đánh dấu hàng đang vận chuyển")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> markInTransit(@PathVariable Integer orderId) {
        return ResponseEntity.ok(ApiResponse.ok("Đã chuyển đơn sang đang vận chuyển", purchaseOrderService.markInTransit(orderId)));
    }

    @PutMapping("/purchase-orders/{orderId}/eta")
    @Operation(summary = "Đổi ngày dự kiến hàng về", description = "ETA trễ hơn requiredBy thì đơn chuyển sang DELAYED (BR_07).")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> updateEta(@PathVariable Integer orderId,
                                                                   @Valid @RequestBody PurchaseOrderEtaRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật ngày dự kiến hàng về thành công", purchaseOrderService.updateEta(orderId, request)));
    }

    @PutMapping("/purchase-orders/{orderId}/cancel")
    @Operation(summary = "Hủy đơn mua hàng", description = "Chỉ hủy được khi chưa nhận hàng.")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> cancelOrder(@PathVariable Integer orderId,
                                                                     @Valid @RequestBody ReasonRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã hủy đơn mua hàng", purchaseOrderService.cancelOrder(orderId, request)));
    }

    @PutMapping("/purchase-orders/{orderId}/receive")
    @Operation(summary = "Nhận hàng và nhập kho", description = "Nhận từng dòng với số lượng và hạn dùng (số lô tùy chọn, để trống thì hệ thống tự sinh). Nhận đủ thì đơn RECEIVED, chưa đủ thì PARTIALLY_RECEIVED.")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> receiveOrder(@PathVariable Integer orderId,
                                                                      @Valid @RequestBody PurchaseOrderReceiveRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Nhận hàng và nhập kho thành công", purchaseOrderService.receiveOrder(orderId, request)));
    }
}
