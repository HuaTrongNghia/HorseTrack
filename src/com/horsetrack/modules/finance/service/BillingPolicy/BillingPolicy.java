package com.horsetrack.equinecare.modules.finance.service;

import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Quy tắc tính tiền của hóa đơn:
 * - BR_12: phát hành ngày 1, hạn thanh toán sau 10 ngày (ngày 10); từ ngày 11 tính phí trễ hạn.
 * - BR_14: phí trễ hạn = gốc x 1% x số ngày quá hạn; khi bị đình chỉ cộng thêm phí kích hoạt lại 3.000.000 VND.
 * - BR_13: từ 25 ngày kể từ ngày phát hành được phép đình chỉ dịch vụ. BR_15: quá hạn hơn 60 ngày.
 */
@Getter
@Component
public class BillingPolicy {

    @Value("${app.billing.payment-term-days:10}")
    private int paymentTermDays;

    @Value("${app.billing.late-fee-rate-per-day:0.01}")
    private BigDecimal lateFeeRatePerDay;

    @Value("${app.billing.reactivation-fee:3000000}")
    private BigDecimal reactivationFee;

    @Value("${app.billing.suspension-days:25}")
    private int suspensionDays;

    @Value("${app.billing.warning-days:20}")
    private int warningDays;

    @Value("${app.billing.termination-days:60}")
    private int terminationDays;

    /** Hạn thanh toán: ngày phát hành là ngày thứ nhất của kỳ hạn (1 → 10) */
    public LocalDate dueDate(LocalDate issueDate) {
        return issueDate.plusDays(paymentTermDays - 1L);
    }

    /** Số ngày quá hạn (0 nếu chưa tới hạn hoặc hóa đơn đã đóng) */
    public long daysOverdue(Invoice invoice, LocalDate today) {
        if (!invoice.isOpen() || invoice.getDueDate() == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(invoice.getDueDate(), today));
    }

    /** Số ngày kể từ ngày phát hành */
    public long daysSinceIssue(Invoice invoice, LocalDate today) {
        return Math.max(0, ChronoUnit.DAYS.between(invoice.getIssuedAt().toLocalDate(), today));
    }

    public BigDecimal lateFee(Invoice invoice, LocalDate today) {
        long days = daysOverdue(invoice, today);
        if (days == 0) {
            return BigDecimal.ZERO;
        }
        return vnd(invoice.getFinalPayableAmount().multiply(lateFeeRatePerDay).multiply(BigDecimal.valueOf(days)));
    }

    /**
     * Số ngày quy đổi "kể từ ngày phát hành" tính theo hạn thanh toán (hạn - kỳ hạn + 1). Với hóa đơn tháng đúng bằng số ngày
     * kể từ ngày phát hành; với hóa đơn giai đoạn phác đồ (phát hành sớm, hạn là ngày bắt đầu giai đoạn) không bị tính sớm.
     */
    public long nominalDaysSinceIssue(Invoice invoice, LocalDate today) {
        return ChronoUnit.DAYS.between(invoice.getDueDate().minusDays(paymentTermDays - 1L), today);
    }

    public boolean suspensionEligible(Invoice invoice, LocalDate today) {
        return invoice.isOpen() && nominalDaysSinceIssue(invoice, today) >= suspensionDays;
    }

    public boolean terminationDue(Invoice invoice, LocalDate today) {
        return daysOverdue(invoice, today) > terminationDays;
    }

    /** Làm tròn về đồng (VND không có số lẻ) */
    public static BigDecimal vnd(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP);
    }
}
