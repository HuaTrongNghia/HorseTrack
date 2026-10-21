package com.horsetrack.equinecare.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VnPayPaymentDTO {
    private Integer transactionId;
    private String txnRef;
    private BigDecimal amount;
    private String paymentUrl;      // Mở URL này để vào trang thanh toán VNPay Sandbox
    private LocalDateTime expireAt;
}
