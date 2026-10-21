package com.horsetrack.equinecare.modules.finance.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Giao dịch thanh toán (bảng Payment_Transactions). Tạo ở trạng thái PENDING_VERIFICATION khi chủ ngựa bấm thanh toán;
 * chỉ chuyển VERIFIED sau khi server xác minh với VNPay (IPN hoặc truy vấn querydr), FAILED nếu VNPay báo không thành công.
 */
@Entity
@Table(name = "Payment_Transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    public static final String PENDING_VERIFICATION = "PENDING_VERIFICATION";
    public static final String VERIFIED = "VERIFIED";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Integer transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    // Số tiền gửi sang VNPay (gốc + phí trễ hạn + phí kích hoạt lại tại thời điểm tạo giao dịch)
    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid;

    // vnp_TxnRef do HorseTrack sinh, duy nhất cho mỗi lần thanh toán
    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    // vnp_TransactionNo do VNPay trả về
    @Column(name = "gateway_trans_id", length = 100)
    private String gatewayTransId;

    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus;

    // Thời điểm tạo giao dịch
    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;
}
