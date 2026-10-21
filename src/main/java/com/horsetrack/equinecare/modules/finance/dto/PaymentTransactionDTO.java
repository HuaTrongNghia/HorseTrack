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
public class PaymentTransactionDTO {
    private Integer transactionId;
    private Integer invoiceId;
    private String invoiceCode;
    private String paymentMethod;
    private BigDecimal amount;
    private String transactionRef;   // vnp_TxnRef
    private String gatewayTransId;   // vnp_TransactionNo
    private String status;           // PENDING_VERIFICATION, VERIFIED, FAILED
    private LocalDateTime createdAt;
    private LocalDateTime verifiedAt;
    private String notes;
}
