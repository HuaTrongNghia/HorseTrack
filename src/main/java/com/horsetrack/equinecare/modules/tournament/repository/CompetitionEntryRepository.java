package com.horsetrack.equinecare.modules.tournament.repository;

import com.horsetrack.equinecare.modules.tournament.entity.CompetitionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompetitionEntryRepository extends JpaRepository<CompetitionEntry, Integer> {

    List<CompetitionEntry> findAllByOrderByProposedAtDesc();

    List<CompetitionEntry> findByHorse_AssignedTrainer_UserIdOrderByProposedAtDesc(Integer trainerId);

    List<CompetitionEntry> findByHorse_Owner_UserIdOrderByProposedAtDesc(Integer ownerId);

    List<CompetitionEntry> findByStatusInOrderByProposedAtAsc(Collection<String> statuses);

    List<CompetitionEntry> findByVetPassedIsNotNullOrderByVetCheckedAtDesc();

    List<CompetitionEntry> findByHorse_HorseIdOrderByProposedAtDesc(Integer horseId);

    Optional<CompetitionEntry> findByEntryCode(String entryCode);

    boolean existsByHorse_HorseIdAndCompetition_CompetitionId(Integer horseId, Integer competitionId);
}
