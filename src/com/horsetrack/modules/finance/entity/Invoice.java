package com.horsetrack.equinecare.modules.finance.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Hóa đơn tháng của một con ngựa (bảng Invoices). Phát hành ngày 1, hạn thanh toán ngày 10 (BR_12). */
@Entity
@Table(name = "Invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    public static final String UNPAID = "UNPAID";
    public static final String OVERDUE = "OVERDUE";
    public static final String PARTIALLY_PAID = "PARTIALLY_PAID";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";
    /** Hóa đơn còn phải thu */
    public static final List<String> OPEN_STATUSES = List.of(UNPAID, OVERDUE, PARTIALLY_PAID);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoice_id")
    private Integer invoiceId;

    @Column(name = "invoice_code", nullable = false, length = 30, unique = true)
    private String invoiceCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // Kỳ tính phí dạng yyyy-MM
    @Column(name = "billing_month", nullable = false, length = 7)
    private String billingMonth;

    @Column(name = "subtotal_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "prize_deduction_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal prizeDeductionAmount;

    // Tiền gốc phải trả (chưa gồm phí trễ hạn / phí kích hoạt lại, các khoản này tính theo ngày khi thanh toán)
    @Column(name = "final_payable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalPayableAmount;

    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineItemId ASC")
    @Builder.Default
    private List<InvoiceLineItem> lines = new ArrayList<>();

    @OneToMany(mappedBy = "invoice")
    @OrderBy("transactionId ASC")
    @Builder.Default
    private List<PaymentTransaction> transactions = new ArrayList<>();

    public boolean isOpen() {
        return OPEN_STATUSES.contains(paymentStatus);
    }
}
