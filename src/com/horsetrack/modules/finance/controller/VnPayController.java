package com.horsetrack.equinecare.modules.finance.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.finance.dto.VnPayResultDTO;
import com.horsetrack.equinecare.modules.finance.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * Điểm nhận kết quả từ VNPay Sandbox (không cần đăng nhập; mọi dữ liệu được kiểm tra chữ ký HMAC-SHA512).
 * - IPN: VNPay gọi server-to-server; khai báo URL này trong trang quản trị merchant Sandbox (cần URL công khai, ví dụ ngrok).
 * - Return: trình duyệt quay về sau khi thanh toán.
 */
@RestController
@RequestMapping("/api/v1/payments/vnpay")
@RequiredArgsConstructor
public class VnPayController {

    private final PaymentService paymentService;

    // Nếu đặt, trang Return chuyển hướng về frontend kèm kết quả; để trống thì trả JSON
    @Value("${app.vnpay.frontend-result-url:}")
    private String frontendResultUrl;

    @GetMapping("/ipn")
    @Operation(summary = "VNPay IPN (server-to-server)", description = "Xác minh chữ ký, mã giao dịch, số tiền; cập nhật giao dịch VERIFIED/FAILED và hóa đơn PAID. Idempotent.")
    public ResponseEntity<Map<String, String>> ipn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(paymentService.handleIpn(params));
    }

    @GetMapping("/return")
    @Operation(summary = "VNPay Return URL", description = "Trình duyệt quay về sau thanh toán. Không tự đánh dấu đã trả: server hỏi lại VNPay (querydr) rồi mới cập nhật.")
    public ResponseEntity<?> vnpayReturn(@RequestParam Map<String, String> params, HttpServletRequest request) {
        VnPayResultDTO result = paymentService.handleReturn(params, ClientIp.of(request));
        if (StringUtils.hasText(frontendResultUrl)) {
            URI target = UriComponentsBuilder.fromUriString(frontendResultUrl)
                    .queryParam("status", result.getStatus())
                    .queryParamIfPresent("invoiceId", Optional.ofNullable(result.getInvoiceId()))
                    .queryParamIfPresent("txnRef", Optional.ofNullable(result.getTxnRef()))
                    .build().encode().toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(target);
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }
        return ResponseEntity.ok(ApiResponse.ok(result.getMessage(), result));
    }
}
