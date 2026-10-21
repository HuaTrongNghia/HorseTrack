package com.horsetrack.equinecare.modules.finance.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.finance.dto.InvoiceDTO;
import com.horsetrack.equinecare.modules.finance.dto.PaymentTransactionDTO;
import com.horsetrack.equinecare.modules.finance.dto.VnPayPaymentDTO;
import com.horsetrack.equinecare.modules.finance.service.InvoiceService;
import com.horsetrack.equinecare.modules.finance.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Hóa đơn và thanh toán VNPay của chủ ngựa (SRS màn hình 14–17) */
@RestController
@RequestMapping("/api/v1/owner/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Horse Owner')")
public class OwnerInvoiceController {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "Hóa đơn của tôi", description = "Mặc định chỉ hóa đơn còn phải trả (UNPAID, OVERDUE). includeClosed=true để xem cả hóa đơn đã thanh toán / đã hủy.")
    public ResponseEntity<ApiResponse<List<InvoiceDTO>>> getMyInvoices(@RequestParam(defaultValue = "false") boolean includeClosed) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách hóa đơn thành công", invoiceService.getMyInvoices(includeClosed)));
    }

    @GetMapping("/payment-history")
    @Operation(summary = "Lịch sử thanh toán", description = "Chỉ giao dịch đã kết thúc: thành công (VERIFIED) hoặc thất bại (FAILED).")
    public ResponseEntity<ApiResponse<List<PaymentTransactionDTO>>> getPaymentHistory() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy lịch sử thanh toán thành công", invoiceService.getMyPaymentHistory()));
    }

    @GetMapping("/{invoiceId}")
    @Operation(summary = "Chi tiết hóa đơn", description = "Dòng phí, hạn thanh toán, phí trễ hạn (BR_12), phí kích hoạt lại (BR_14), số tiền phải trả hôm nay và lịch sử giao dịch.")
    public ResponseEntity<ApiResponse<InvoiceDTO>> getMyInvoice(@PathVariable Integer invoiceId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết hóa đơn thành công", invoiceService.getMyInvoice(invoiceId)));
    }

    @PostMapping("/{invoiceId}/vnpay")
    @Operation(summary = "Thanh toán qua VNPay Sandbox", description = "Tạo giao dịch chờ xác minh và trả paymentUrl. Mở paymentUrl để thanh toán; hóa đơn chỉ chuyển PAID sau khi server xác minh với VNPay.")
    public ResponseEntity<ApiResponse<VnPayPaymentDTO>> payWithVnPay(@PathVariable Integer invoiceId, HttpServletRequest request) {
        VnPayPaymentDTO dto = paymentService.createVnPayPayment(invoiceId, ClientIp.of(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đã tạo giao dịch VNPay. Mở paymentUrl để thanh toán.", dto));
    }
}
