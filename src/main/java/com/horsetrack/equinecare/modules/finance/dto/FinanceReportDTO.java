package com.horsetrack.equinecare.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Báo cáo tài chính cho Club Manager. Tiền đã thu chỉ tính giao dịch VNPay được server xác minh (VERIFIED). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReportDTO {
    private String fromMonth;
    private String toMonth;
    private LocalDateTime generatedAt;
    private Totals totals;
    private List<MonthRow> months;
    private List<CategoryRow> revenueByService;
    private List<AgeingBucket> debtAgeing;
    private List<OwnerDebtRow> debtByOwner;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Totals {
        private BigDecimal billed;               // Tổng phát hành (gốc) các kỳ trong khoảng
        private BigDecimal collected;            // Tổng đã thu (VERIFIED) trong khoảng
        private BigDecimal collectionRate;       // % = đã thu của các hóa đơn trong khoảng / phát hành
        private BigDecimal lateFeesCollected;    // Phí trễ hạn + phí kích hoạt lại đã thu
        private BigDecimal outstandingPrincipal; // Gốc còn phải thu (mọi hóa đơn đang mở)
        private BigDecimal outstandingLateFees;  // Phí trễ hạn đang phát sinh tới hôm nay
        private BigDecimal purchaseCost;         // Chi phí nhập hàng (đơn mua đã nhận) trong khoảng
        private BigDecimal netCashFlow;          // Đã thu - chi phí nhập hàng
        private long invoicesIssued;
        private long invoicesPaid;
        private long invoicesOverdue;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MonthRow {
        private String month;                // yyyy-MM
        private BigDecimal billed;           // Phát hành cho kỳ này
        private BigDecimal collected;        // Thu được trong tháng này (theo ngày xác minh)
        private BigDecimal collectionRate;   // % gốc đã thu của các hóa đơn kỳ này
        private BigDecimal purchaseCost;     // Chi phí nhập hàng nhận trong tháng
        private long invoiceCount;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CategoryRow {
        private String category;
        private String label;
        private BigDecimal billed;      // Phát hành
        private BigDecimal collected;   // Thuộc các hóa đơn đã thanh toán
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AgeingBucket {
        private String bucket;          // NOT_DUE, OVERDUE_1_15, OVERDUE_16_60, OVERDUE_OVER_60
        private String label;
        private long invoiceCount;
        private BigDecimal principal;
        private BigDecimal lateFees;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OwnerDebtRow {
        private Integer ownerId;
        private String ownerName;
        private String email;
        private String serviceStatus;
        private long openInvoices;
        private BigDecimal principal;
        private BigDecimal lateFees;
        private BigDecimal totalDue;
        private long maxDaysOverdue;
        private boolean suspensionEligible;
        private boolean terminationDue;
    }
}
