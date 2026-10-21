package com.horsetrack.equinecare.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VnPayResultDTO {
    private String status;          // SUCCESS, FAILED, PENDING, INVALID
    private String message;
    private Integer invoiceId;
    private String invoiceCode;
    private String invoiceStatus;
    private String txnRef;
    private BigDecimal amount;
    private String responseCode;    // vnp_ResponseCode
}
