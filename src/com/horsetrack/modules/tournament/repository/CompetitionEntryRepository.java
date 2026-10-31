package com.horsetrack.equinecare.modules.tournament.repository;

import com.horsetrack.equinecare.modules.tournament.entity.CompetitionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompetitionEntryRepository extends JpaRepository<CompetitionEntry, Integer> {

    List<CompetitionEntry> findAllByOrderByProposedAtDesc();

    List<CompetitionEntry> findByHorse_AssignedTrainer_UserIdOrderByProposedAtDesc(Integer trainerId);

    List<CompetitionEntry> findByOwner_UserIdOrderByProposedAtDesc(Integer ownerId);

    List<CompetitionEntry> findByStatusInOrderByProposedAtAsc(Collection<String> statuses);

    List<CompetitionEntry> findByVetPassedIsNotNullOrderByVetCheckedAtDesc();

    List<CompetitionEntry> findByHorse_HorseIdOrderByProposedAtDesc(Integer horseId);

    Optional<CompetitionEntry> findByEntryCode(String entryCode);

    Optional<CompetitionEntry> findFirstByHorse_HorseIdAndCompetition_CompetitionIdAndStatus(Integer horseId, Integer competitionId, String status);

    Optional<CompetitionEntry> findFirstByInvoice_InvoiceId(Integer invoiceId);

    // Đề xuất của một giải theo thứ tự đề xuất (dùng cho giữ chỗ / hàng chờ)
    List<CompetitionEntry> findByCompetition_CompetitionIdAndStatusInOrderByProposedAtAscEntryIdAsc(Integer competitionId, Collection<String> statuses);

    List<CompetitionEntry> findByCompetition_CompetitionIdOrderByProposedAtAscEntryIdAsc(Integer competitionId);

    long countByCompetition_CompetitionIdAndStatusIn(Integer competitionId, Collection<String> statuses);

    boolean existsByCompetition_CompetitionId(Integer competitionId);

    List<CompetitionEntry> findByCompetition_CompetitionIdAndOwner_UserIdOrderByProposedAtDesc(Integer competitionId, Integer ownerId);

    boolean existsByCompetition_CompetitionIdAndOwner_UserIdAndComplaintStatus(Integer competitionId, Integer ownerId, String complaintStatus);

    List<CompetitionEntry> findByComplaintStatusOrderByComplaintAtAsc(String complaintStatus);

    // Ngựa đã có đề xuất còn hiệu lực ở giải khác cùng ngày thi
    @Query("SELECT e FROM CompetitionEntry e WHERE e.horse.horseId = :horseId AND e.status IN :statuses "
            + "AND e.competition.startDate = :raceDate AND e.competition.competitionId <> :competitionId")
    List<CompetitionEntry> findSameDayEntries(@Param("horseId") Integer horseId, @Param("raceDate") LocalDate raceDate,
                                              @Param("competitionId") Integer competitionId, @Param("statuses") Collection<String> statuses);

    // Đề xuất của ngựa (đã được HLV xác nhận trở đi) có ngày thi trong khoảng [from, to]
    @Query("SELECT e FROM CompetitionEntry e WHERE e.horse.horseId = :horseId AND e.status IN :statuses "
            + "AND e.competition.startDate BETWEEN :from AND :to")
    List<CompetitionEntry> findByHorseAndRaceDateBetween(@Param("horseId") Integer horseId, @Param("statuses") Collection<String> statuses,
                                                         @Param("from") LocalDate from, @Param("to") LocalDate to);
}
