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
    /** WAITING_OWNER, OWNER_DECLINED, WAITING_VET, VET_FAILED, READY_TO_SUBMIT, SUBMITTED, REJECTED, RESULT_RECORDED */
    private String status;
    private String statusLabel;
    private Integer horseId;
    private String horseName;
    private Integer ownerId;
    private String ownerName;
    private TournamentDTO tournament;
    private Integer proposedById;
    private String proposedByName;
    private LocalDateTime proposedAt;
    private String note;
    private Boolean ownerAgreed;
    private LocalDateTime ownerRespondedAt;
    private String ownerNote;
    private String vetName;
    private Boolean vetPassed;
    private LocalDateTime vetCheckedAt;
    private String vetNote;
    private BigDecimal entryFee;
    private Integer rank;
    private BigDecimal finishTimeSeconds;
    private String resultNote;
}
