package com.horsetrack.equinecare.modules.tournament.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Giải đấu do Quản lý CLB tạo: DRAFT (đang soạn) → PUBLISHED (công bố, chủ ngựa đề xuất) → FINISHED (đã nhập kết quả).
 * Điều kiện tham gia (tuổi, cân nặng, giống) dùng để hệ thống tự lọc ngựa; slots là số suất chính thức.
 */
@Entity
@Table(name = "External_Competitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalCompetition {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String FINISHED = "FINISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "competition_id")
    private Integer competitionId;

    @Column(name = "competition_code", length = 30)
    private String competitionCode;

    @Column(name = "competition_name", nullable = false, length = 150)
    private String competitionName;

    @Column(name = "organizer", nullable = false, length = 150)
    private String organizer;

    @Column(name = "location", nullable = false, length = 100)
    private String location;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "registration_deadline")
    private LocalDate registrationDeadline;

    @Column(name = "race_distance_m", nullable = false, precision = 10, scale = 2)
    private BigDecimal raceDistanceM;

    @Column(name = "standard_entry_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal standardEntryFee;

    @Column(name = "entry_conditions", length = 500)
    private String entryConditions;

    @Column(name = "source_note", length = 255)
    private String sourceNote;

    @Column(name = "is_finished", nullable = false)
    private Boolean isFinished;

    @Column(name = "min_age_years")
    private Integer minAgeYears;

    @Column(name = "max_age_years")
    private Integer maxAgeYears;

    @Column(name = "min_weight_kg", precision = 6, scale = 2)
    private BigDecimal minWeightKg;

    @Column(name = "max_weight_kg", precision = 6, scale = 2)
    private BigDecimal maxWeightKg;

    // Các giống được phép, ngăn cách bằng ';' (trống = mọi giống)
    @Column(name = "allowed_breeds", length = 500)
    private String allowedBreeds;

    @Column(name = "slots", nullable = false)
    private Integer slots;

    @Column(name = "tournament_status", nullable = false, length = 20)
    private String status;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // Bảng thưởng: hạng -> tiền thưởng
    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "Competition_Prizes", joinColumns = @JoinColumn(name = "competition_id"))
    @MapKeyColumn(name = "rank_no")
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private Map<Integer, BigDecimal> prizes = new HashMap<>();

    /** Thời điểm hết hạn đăng ký chung: cuối ngày registrationDeadline (không có thì đầu ngày thi). Không hạn chặng nào vượt quá mốc này. */
    public LocalDateTime registrationCloseAt() {
        return registrationDeadline != null ? registrationDeadline.plusDays(1).atStartOfDay() : startDate.atStartOfDay();
    }
}
