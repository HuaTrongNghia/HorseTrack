package com.horsetrack.equinecare.modules.medical.repository;

import com.horsetrack.equinecare.modules.medical.entity.TreatmentProtocol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TreatmentProtocolRepository extends JpaRepository<TreatmentProtocol, Integer> {

    // Danh sách phác đồ, mới nhất trước; tham số null thì bỏ qua
    @Query("SELECT p FROM TreatmentProtocol p " +
            "WHERE (:state IS NULL OR p.state = :state) " +
            "AND (:horseId IS NULL OR p.horse.horseId = :horseId) " +
            "ORDER BY p.createdAt DESC, p.protocolId DESC")
    List<TreatmentProtocol> search(@Param("state") String state, @Param("horseId") Integer horseId);

    List<TreatmentProtocol> findByStateInOrderByRequiredByAscCreatedAtAsc(Collection<String> states);

    // Phiên bản thay thế (bản mới) của một phác đồ
    Optional<TreatmentProtocol> findByReplacesProtocol_ProtocolId(Integer protocolId);
}
