package com.horsetrack.equinecare.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Một khoản cần hoàn / đã hoàn trên màn hình Hoàn tiền của Quản lý CLB */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundDTO {
    private Integer refundId;
    private Integer invoiceId;
    private String invoiceCode;
    private String horseName;
    private Integer ownerId;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    private BigDecimal amount;
    private String reason;
    /** PENDING (chờ hoàn) / DONE (đã hoàn) */
    private String status;
    private String statusLabel;
    private LocalDateTime requestedAt;
    private String transactionRef;
    private LocalDate transferredOn;
    private String transferredBy;
    private String confirmedByName;
    private LocalDateTime confirmedAt;
    private String note;
    /** Hiện nút "Xác nhận đã hoàn" */
    private Boolean canConfirm;
}
