package com.horsetrack.equinecare.modules.tournament.repository;

import com.horsetrack.equinecare.modules.tournament.entity.ExternalCompetition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalCompetitionRepository extends JpaRepository<ExternalCompetition, Integer> {

    List<ExternalCompetition> findAllByOrderByStartDateDesc();

    List<ExternalCompetition> findByStatusInOrderByStartDateDesc(Collection<String> statuses);

    List<ExternalCompetition> findByStatusOrderByStartDateAsc(String status);

    boolean existsByCompetitionCode(String competitionCode);

    boolean existsByCompetitionCodeAndCompetitionIdNot(String competitionCode, Integer competitionId);

    // Khóa dòng giải đấu khi giữ chỗ / đôn hàng chờ để 2 yêu cầu song song không cùng lấy 1 suất
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ExternalCompetition c WHERE c.competitionId = :id")
    Optional<ExternalCompetition> findByIdForUpdate(@Param("id") Integer id);
}
