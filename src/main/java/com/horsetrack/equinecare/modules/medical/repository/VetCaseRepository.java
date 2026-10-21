package com.horsetrack.equinecare.modules.medical.repository;

import com.horsetrack.equinecare.modules.medical.entity.VetCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VetCaseRepository extends JpaRepository<VetCase, Integer> {

    // Ca khám mới nhất trước; reportedById/status null thì bỏ qua
    @Query("SELECT c FROM VetCase c " +
            "WHERE (:status IS NULL OR c.status = :status) " +
            "AND (:reportedById IS NULL OR c.reportedBy.userId = :reportedById) " +
            "ORDER BY c.reportedAt DESC, c.caseId DESC")
    List<VetCase> search(@Param("status") String status, @Param("reportedById") Integer reportedById);

    Optional<VetCase> findByCaseCode(String caseCode);
}
