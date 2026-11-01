package com.horsetrack.equinecare.modules.tournament.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Đề xuất thi đấu của chủ ngựa. Còn suất thì giữ chỗ ngay (WAITING_TRAINER), hết suất thì vào hàng chờ (WAITLISTED).
 * Giữ chỗ qua 3 chặng có hạn: HLV xác nhận (2 ngày) → bác sĩ khám (3 ngày) → thanh toán lệ phí (2 ngày) → REGISTERED.
 * Mất suất: TRAINER_REJECTED / TRAINER_EXPIRED / VET_FAILED / VET_EXPIRED / PAYMENT_EXPIRED / CANCELLED / INELIGIBLE;
 * hàng chờ còn lại khi hết hạn đăng ký: NO_SLOT. Có kết quả: RESULT_RECORDED.
 */
@Entity
@Table(name = "Competition_Entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompetitionEntry {

    public static final String WAITLISTED = "WAITLISTED";
    public static final String WAITING_TRAINER = "WAITING_TRAINER";
    public static final String TRAINER_REJECTED = "TRAINER_REJECTED";
    public static final String TRAINER_EXPIRED = "TRAINER_EXPIRED";
    public static final String WAITING_VET_EXAM = "WAITING_VET_EXAM";
    public static final String VET_FAILED = "VET_FAILED";
    public static final String VET_EXPIRED = "VET_EXPIRED";
    public static final String WAITING_PAYMENT = "WAITING_PAYMENT";
    public static final String PAYMENT_EXPIRED = "PAYMENT_EXPIRED";
    public static final String REGISTERED = "REGISTERED";
    public static final String RESULT_RECORDED = "RESULT_RECORDED";
    public static final String CANCELLED = "CANCELLED";
    public static final String INELIGIBLE = "INELIGIBLE";
    public static final String NO_SLOT = "NO_SLOT";

    /** Đang chiếm suất chính thức */
    public static final List<String> HOLDING = List.of(WAITING_TRAINER, WAITING_VET_EXAM, WAITING_PAYMENT, REGISTERED, RESULT_RECORDED);
    /** Còn hiệu lực (giữ chỗ hoặc hàng chờ): mỗi chủ ngựa / mỗi ngựa chỉ có 1 đề xuất như vậy cho mỗi giải */
    public static final List<String> ACTIVE = List.of(WAITLISTED, WAITING_TRAINER, WAITING_VET_EXAM, WAITING_PAYMENT, REGISTERED);
    /** Đang chạy 3 chặng (có hạn chặng) */
    public static final List<String> IN_STAGE = List.of(WAITING_TRAINER, WAITING_VET_EXAM, WAITING_PAYMENT);
    /** Đã được HLV xác nhận trở đi (áp dụng chặn giáo án cường độ Cao 7 ngày trước giải) */
    public static final List<String> TRAINER_CONFIRMED = List.of(WAITING_VET_EXAM, WAITING_PAYMENT, REGISTERED);

    public static final String COMPLAINT_PENDING = "PENDING";
    public static final String COMPLAINT_ACCEPTED = "ACCEPTED";
    public static final String COMPLAINT_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Integer entryId;

    // Cột tính toán DX-0001, đọc lại sau khi thêm
    @org.hibernate.annotations.Generated
    @Column(name = "entry_code", insertable = false, updatable = false)
    private String entryCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competition_id", nullable = false)
    private ExternalCompetition competition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    // Chủ ngựa tại thời điểm đề xuất (người đề xuất)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "registration_status", nullable = false, length = 20)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_by")
    private User proposedBy;

    // Thứ tự giữ chỗ / hàng chờ theo thời điểm đề xuất
    @Column(name = "proposed_at", nullable = false)
    private LocalDateTime proposedAt;

    @Column(name = "proposal_note", length = 500)
    private String proposalNote;

    @Column(name = "slot_held_at")
    private LocalDateTime slotHeldAt;

    // Hạn của chặng hiện tại (không vượt quá hạn đăng ký chung của giải)
    @Column(name = "stage_deadline")
    private LocalDateTime stageDeadline;

    @Column(name = "deadline_reminded", nullable = false)
    private Boolean deadlineReminded;

    // Phong độ của ngựa lúc đề xuất (0-100)
    @Column(name = "form_score", precision = 5, scale = 2)
    private BigDecimal formScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trainer_id")
    private User trainer;

    @Column(name = "trainer_approved")
    private Boolean trainerApproved;

    @Column(name = "trainer_decided_at")
    private LocalDateTime trainerDecidedAt;

    @Column(name = "trainer_reason_code", length = 30)
    private String trainerReasonCode;

    @Column(name = "trainer_note", length = 500)
    private String trainerNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vet_id")
    private User vet;

    @Column(name = "vet_passed")
    private Boolean vetPassed;

    @Column(name = "vet_checked_at")
    private LocalDateTime vetCheckedAt;

    @Column(name = "vet_note", length = 500)
    private String vetNote;

    // Hóa đơn lệ phí đăng ký (lập khi khám đạt)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "complaint_status", length = 20)
    private String complaintStatus;

    @Column(name = "complaint_note", length = 500)
    private String complaintNote;

    @Column(name = "complaint_at")
    private LocalDateTime complaintAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_decided_by")
    private User complaintDecidedBy;

    @Column(name = "complaint_decided_at")
    private LocalDateTime complaintDecidedAt;

    @Column(name = "complaint_decision_note", length = 500)
    private String complaintDecisionNote;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_reason", length = 500)
    private String closedReason;

    @Column(name = "entry_fee_charged", nullable = false, precision = 18, scale = 2)
    private BigDecimal entryFeeCharged;

    @Column(name = "transport_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal transportFee;

    @Column(name = "prize_awarded", nullable = false, precision = 18, scale = 2)
    private BigDecimal prizeAwarded;

    @Column(name = "rank_position")
    private Integer rankPosition;

    @Column(name = "finish_time_seconds", precision = 10, scale = 3)
    private BigDecimal finishTimeSeconds;

    @Column(name = "result_note", length = 500)
    private String resultNote;
}
