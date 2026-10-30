package com.horsetrack.equinecare.modules.tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceProposalDTO {
    private Integer proposalId;
    private String proposalCode;
    /**
     * WAITLISTED, WAITING_TRAINER, TRAINER_REJECTED, TRAINER_EXPIRED, WAITING_VET_EXAM, VET_FAILED, VET_EXPIRED,
     * WAITING_PAYMENT, PAYMENT_EXPIRED, REGISTERED, RESULT_RECORDED, CANCELLED, INELIGIBLE, NO_SLOT
     */
    private String status;
    private String statusLabel;
    /** Đang giữ suất chính thức */
    private Boolean holdingSlot;
    /** Hạn của chặng hiện tại (chỉ có khi WAITING_TRAINER / WAITING_VET_EXAM / WAITING_PAYMENT) */
    private LocalDateTime stageDeadline;
    /** Vị trí trong hàng chờ, 1 = đứng đầu (chỉ có khi WAITLISTED) */
    private Integer queuePosition;
    private Integer horseId;
    private String horseName;
    private Integer ownerId;
    private String ownerName;
    private Integer trainerId;
    private String trainerName;
    private TournamentDTO tournament;
    private LocalDateTime proposedAt;
    private String note;
    private BigDecimal formScore;
    private LocalDateTime slotHeldAt;
    private Boolean trainerApproved;
    private LocalDateTime trainerDecidedAt;
    private String trainerReasonCode;
    private String trainerReasonLabel;
    private String trainerNote;
    private String vetName;
    private Boolean vetPassed;
    private LocalDateTime vetCheckedAt;
    private String vetNote;
    private BigDecimal entryFee;
    private Integer invoiceId;
    private String invoiceCode;
    private String invoiceStatus;
    private LocalDateTime paidAt;
    /** PENDING, ACCEPTED, REJECTED */
    private String complaintStatus;
    private String complaintNote;
    private LocalDateTime complaintAt;
    private String complaintDecisionNote;
    private LocalDateTime complaintDecidedAt;
    /** Chủ ngựa được gửi khiếu nại (HLV từ chối, chưa khiếu nại, giải còn hạn đăng ký) */
    private Boolean canComplain;
    private String closedReason;
    private LocalDateTime closedAt;
    private Integer rank;
    private BigDecimal finishTimeSeconds;
    private BigDecimal prizeAmount;
    private String resultNote;
}
