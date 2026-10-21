package com.horsetrack.equinecare.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDTO {
    private Integer invoiceId;
    private String invoiceCode;
    private Integer horseId;
    private String horseName;
    private Integer ownerId;
    private String ownerName;
    private String ownerEmail;
    private String ownerServiceStatus;   // ACTIVE, SUSPENDED, REACTIVATION_PENDING
    private String billingMonth;
    private LocalDateTime issuedAt;
    private LocalDate dueDate;
    private String status;               // UNPAID, OVERDUE, PAID, CANCELLED...

    private BigDecimal subtotal;
    private BigDecimal prizeDeduction;
    private BigDecimal principal;        // Gốc phải trả
    private BigDecimal lateFee;          // Phí trễ hạn tính tới hôm nay (BR_12, BR_14)
    private BigDecimal reactivationFee;  // Phí kích hoạt lại nếu đang bị đình chỉ (BR_14)
    private BigDecimal amountDue;        // Số tiền thanh toán qua VNPay nếu trả hôm nay
    private BigDecimal amountPaid;       // Đã thu (giao dịch VERIFIED)

    private long daysOverdue;
    private long daysSinceIssue;
    private boolean suspensionEligible;  // >= 25 ngày kể từ ngày phát hành (BR_13)
    private boolean terminationDue;      // quá hạn > 60 ngày (BR_15)

    private List<InvoiceLineDTO> lines;               // Chỉ có ở API chi tiết
    private List<PaymentTransactionDTO> transactions; // Chỉ có ở API chi tiết
}
