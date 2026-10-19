package com.horsetrack.repository;

import com.horsetrack.entity.TreatmentProtocol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TreatmentProtocolRepository extends JpaRepository<TreatmentProtocol, Integer> {
}
