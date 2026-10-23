package com.horsetrack.equinecare.modules.tournament.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Đề xuất thi đấu: HLV đề xuất (WAITING_OWNER) → chủ ngựa đồng ý (WAITING_VET) / từ chối (OWNER_DECLINED)
 * → bác sĩ khám tiền thi đấu đạt (READY_TO_SUBMIT) / không đạt (VET_FAILED) → HLV gửi BTC (SUBMITTED)
 * → BTC từ chối (REJECTED) hoặc ghi kết quả (RESULT_RECORDED).
 */
@Entity
@Table(name = "Competition_Entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompetitionEntry {

    public static final String WAITING_OWNER = "WAITING_OWNER";
    public static final String OWNER_DECLINED = "OWNER_DECLINED";
    public static final String WAITING_VET = "WAITING_VET";
    public static final String VET_FAILED = "VET_FAILED";
    public static final String READY_TO_SUBMIT = "READY_TO_SUBMIT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String RESULT_RECORDED = "RESULT_RECORDED";

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

    @Column(name = "registration_status", nullable = false, length = 20)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_by")
    private User proposedBy;

    @Column(name = "proposed_at", nullable = false)
    private LocalDateTime proposedAt;

    @Column(name = "proposal_note", length = 500)
    private String proposalNote;

    @Column(name = "owner_agreed")
    private Boolean ownerAgreed;

    @Column(name = "owner_responded_at")
    private LocalDateTime ownerRespondedAt;

    @Column(name = "owner_response_note", length = 500)
    private String ownerResponseNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vet_id")
    private User vet;

    @Column(name = "vet_passed")
    private Boolean vetPassed;

    @Column(name = "vet_checked_at")
    private LocalDateTime vetCheckedAt;

    @Column(name = "vet_note", length = 500)
    private String vetNote;

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
