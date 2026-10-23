package com.horsetrack.equinecare.modules.tournament.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Giải đua bên ngoài mà CLB có thể đăng ký ngựa tham gia */
@Entity
@Table(name = "External_Competitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalCompetition {

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
}
