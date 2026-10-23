package com.horsetrack.equinecare.modules.tournament.repository;

import com.horsetrack.equinecare.modules.tournament.entity.ExternalCompetition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExternalCompetitionRepository extends JpaRepository<ExternalCompetition, Integer> {

    List<ExternalCompetition> findAllByOrderByStartDateDesc();

    List<ExternalCompetition> findByIsFinishedFalseOrderByStartDateAsc();

    boolean existsByCompetitionCode(String competitionCode);
}
