package com.horsetrack.equinecare.modules.finance.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.finance.dto.*;
import com.horsetrack.equinecare.modules.finance.service.InvoiceGenerator;
import com.horsetrack.equinecare.modules.finance.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Quản lý hóa đơn, đình chỉ / kích hoạt lại dịch vụ và phí lưu chuồng (Club Manager, SRS màn hình 23–25) */
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Club Manager')")
public class BillingController {

    private final InvoiceService invoiceService;
    private final InvoiceGenerator invoiceGenerator;

    @GetMapping("/invoices")
    @Operation(summary = "Tất cả hóa đơn", description = "Lọc tùy chọn: status (UNPAID, OVERDUE, PAID, CANCELLED), billingMonth (yyyy-MM), ownerId. Có số ngày quá hạn, phí trễ hạn, cờ đủ điều kiện đình chỉ.")
    public ResponseEntity<ApiResponse<Page<InvoiceDTO>>> getInvoices(@RequestParam(required = false) String status,
                                                                     @RequestParam(required = false) String billingMonth,
                                                                     @RequestParam(required = false) Integer ownerId,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách hóa đơn thành công", invoiceService.searchInvoices(status, billingMonth, ownerId, page, size)));
    }

    @GetMapping("/invoices/{invoiceId}")
    @Operation(summary = "Chi tiết hóa đơn", description = "Dòng phí, hạn thanh toán, phí trễ hạn và lịch sử giao dịch.")
    public ResponseEntity<ApiResponse<InvoiceDTO>> getInvoice(@PathVariable Integer invoiceId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết hóa đơn thành công", invoiceService.getInvoice(invoiceId)));
    }

    @PostMapping("/invoices/generate")
    @Operation(summary = "Phát hành hóa đơn tháng", description = "Lập hóa đơn kỳ billingMonth (yyyy-MM) cho mọi ngựa đang ở CLB từ chi phí thật trong DB. Hệ thống tự chạy ngày 1 hằng tháng cho kỳ tháng trước; gọi lại không tạo trùng.")
    public ResponseEntity<ApiResponse<InvoiceGenerateResultDTO>> generate(@RequestParam String billingMonth) {
        YearMonth period;
        try {
            period = YearMonth.parse(billingMonth.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("billingMonth phải có dạng yyyy-MM (ví dụ 2026-10).");
        }
        InvoiceGenerateResultDTO r = invoiceGenerator.generate(period, LocalDate.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đã phát hành " + r.getCreated() + " hóa đơn kỳ " + r.getBillingMonth(), r));
    }

    @PutMapping("/invoices/{invoiceId}/cancel")
    @Operation(summary = "Hủy hóa đơn", description = "Club Manager hủy hóa đơn chưa thanh toán. Bắt buộc lý do.")
    public ResponseEntity<ApiResponse<InvoiceDTO>> cancel(@PathVariable Integer invoiceId, @Valid @RequestBody CancelInvoiceRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã hủy hóa đơn", invoiceService.cancelInvoice(invoiceId, request.getReason())));
    }

    @GetMapping("/owners/{ownerId}/service")
    @Operation(summary = "Trạng thái dịch vụ của chủ ngựa", description = "Đang hoạt động, đình chỉ hoặc chờ kích hoạt lại.")
    public ResponseEntity<ApiResponse<OwnerServiceDTO>> getOwnerService(@PathVariable Integer ownerId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy trạng thái dịch vụ thành công", invoiceService.getOwnerService(ownerId)));
    }

    @PutMapping("/owners/{ownerId}/suspend")
    @Operation(summary = "Đình chỉ dịch vụ (BR_13)", description = "Chỉ khi chủ ngựa có hóa đơn chưa trả từ 25 ngày kể từ ngày phát hành. Lý do 10–255 ký tự.")
    public ResponseEntity<ApiResponse<OwnerServiceDTO>> suspend(@PathVariable Integer ownerId, @Valid @RequestBody SuspendServiceRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã đình chỉ dịch vụ của chủ ngựa", invoiceService.suspendService(ownerId, request.getReason())));
    }

    @PutMapping("/owners/{ownerId}/reactivate")
    @Operation(summary = "Kích hoạt lại dịch vụ (BR_14)", description = "Chỉ khi chủ ngựa đã trả đủ và giao dịch VNPay đã được xác minh (REACTIVATION_PENDING).")
    public ResponseEntity<ApiResponse<OwnerServiceDTO>> reactivate(@PathVariable Integer ownerId) {
        return ResponseEntity.ok(ApiResponse.ok("Đã kích hoạt lại dịch vụ", invoiceService.reactivateService(ownerId)));
    }

    @PutMapping("/stalls/{stallId}/monthly-fee")
    @Operation(summary = "Đặt phí lưu chuồng tháng", description = "Giá trị dùng cho dòng BOARDING khi phát hành hóa đơn.")
    public ResponseEntity<ApiResponse<BigDecimal>> updateStallFee(@PathVariable Integer stallId, @Valid @RequestBody StallFeeRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật phí lưu chuồng", invoiceService.updateStallFee(stallId, request.getMonthlyBaseFee())));
    }
}
