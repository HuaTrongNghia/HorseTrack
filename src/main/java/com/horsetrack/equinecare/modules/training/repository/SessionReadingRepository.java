package com.horsetrack.equinecare.modules.training.repository;

import com.horsetrack.equinecare.modules.training.entity.SessionReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionReadingRepository extends JpaRepository<SessionReading, Long> {

    List<SessionReading> findBySession_SessionIdInOrderByRecordedAtAscReadingIdAsc(Collection<Integer> sessionIds);

    Optional<SessionReading> findFirstBySession_SessionIdAndReadingPhaseOrderByRecordedAtDescReadingIdDesc(Integer sessionId, String readingPhase);
}
