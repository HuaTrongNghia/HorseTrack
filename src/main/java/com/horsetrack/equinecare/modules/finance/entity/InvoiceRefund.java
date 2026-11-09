package com.horsetrack.equinecare.modules.finance.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Khoản hoàn tiền của một hóa đơn đã thanh toán (hủy giai đoạn phác đồ, hủy đăng ký giải...).
 * PENDING: chờ CLB chuyển tiền (hóa đơn REFUND_PENDING). DONE: Quản lý CLB đã xác nhận chuyển (hóa đơn REFUNDED).
 */
@Entity
@Table(name = "Invoice_Refunds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceRefund {

    public static final String PENDING = "PENDING";
    public static final String DONE = "DONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    private Integer refundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    // Mã giao dịch chuyển khoản hoàn tiền (ngân hàng / VNPay)
    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    @Column(name = "transferred_on")
    private LocalDate transferredOn;

    // Người thực hiện chuyển tiền (có thể là kế toán, không bắt buộc có tài khoản)
    @Column(name = "transferred_by", length = 150)
    private String transferredBy;

    // Quản lý CLB xác nhận trên hệ thống
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "note", length = 500)
    private String note;
}
