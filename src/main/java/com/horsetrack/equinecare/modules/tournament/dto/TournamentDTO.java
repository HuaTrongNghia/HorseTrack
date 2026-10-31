package com.horsetrack.equinecare.modules.tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    private Integer minAgeYears;
    private Integer maxAgeYears;
    private BigDecimal minWeightKg;
    private BigDecimal maxWeightKg;
    private List<String> allowedBreeds;
    private Integer slots;
    private List<PrizeDTO> prizes;
    /** DRAFT, PUBLISHED, FINISHED */
    private String status;
    private String statusLabel;
    private LocalDateTime publishedAt;
    private Boolean finished;
    /** Quản lý CLB đã bấm "Kết thúc giải", đang chờ nhập đủ kết quả (status vẫn PUBLISHED) */
    private Boolean raceEnded;
    /** Hiện nút "Kết thúc giải" (giải đang công bố, chưa kết thúc) */
    private Boolean canFinish;
    /** Được nhập kết quả: giải đang công bố (kể cả trước ngày thi; lần nhập đầu tự kết thúc giải) */
    private Boolean canRecordResults;
    /** Còn nhận đề xuất: đã công bố, chưa qua hạn đăng ký */
    private Boolean openForProposal;
    /** Số suất chính thức còn trống */
    private Integer slotsLeft;
    /** Số đề xuất đang trong hàng chờ */
    private Integer queueLength;
    /** Đã có người đề xuất: không sửa được điều kiện, ngày thi, hạn đăng ký, lệ phí */
    private Boolean hasProposals;
}
