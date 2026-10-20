package com.horsetrack.equinecare.modules.audit.repository;

import com.horsetrack.equinecare.modules.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {

    List<AuditLog> findTop100ByOrderByTimestampDesc();

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    // Tìm log khóa gần nhất của người dùng
    Optional<AuditLog> findFirstByTargetTableAndActionTypeAndNewValueContainingOrderByTimestampDesc(
            String targetTable, String actionType, String userIdentifier);
}
