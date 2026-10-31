package com.horsetrack.equinecare.modules.tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Một giải trên màn hình chủ ngựa: ngựa nào phù hợp, đề xuất hiện tại, suất còn lại, hàng chờ */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerTournamentDTO {
    private TournamentDTO tournament;
    /** Có ít nhất 1 ngựa phù hợp */
    private Boolean eligible;
    /** Ngựa của chủ, phù hợp trước, xếp theo phong độ giảm dần */
    private List<HorseEligibilityDTO> horses;
    private Integer recommendedHorseId;
    /** Đề xuất còn hiệu lực của chủ ngựa cho giải (null nếu không có: được đề xuất ngựa khác khi giải còn mở) */
    private RaceProposalDTO myProposal;
    /** Đề xuất gần nhất đã đóng (VD: khám không đạt, HLV từ chối) khi không có đề xuất còn hiệu lực, để hiển thị lịch sử */
    private RaceProposalDTO lastProposal;
    private Integer slotsLeft;
    private Integer queueLength;
    private Boolean canPropose;
    private String cannotProposeReason;
}
