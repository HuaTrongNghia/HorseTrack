package com.horsetrack.equinecare.modules.tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentDTO {
    private Integer tournamentId;
    private String code;
    private String name;
    private String organizer;
    private String location;
    private LocalDate raceDate;
    private LocalDate registrationDeadline;
    private BigDecimal distanceM;
    private BigDecimal entryFee;
    private String entryConditions;
    private Boolean finished;
    /** Còn nhận đề xuất: chưa kết thúc, chưa qua hạn đăng ký và ngày đua */
    private Boolean openForProposal;
}
