package com.horsetrack.equinecare.modules.medical.repository;

import com.horsetrack.equinecare.modules.medical.entity.ProtocolProgressNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProtocolProgressNoteRepository extends JpaRepository<ProtocolProgressNote, Integer> {

    List<ProtocolProgressNote> findByProtocol_ProtocolIdOrderByWrittenAtAsc(Integer protocolId);
}
