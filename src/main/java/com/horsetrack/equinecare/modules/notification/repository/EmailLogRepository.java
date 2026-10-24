package com.horsetrack.equinecare.modules.notification.repository;

import com.horsetrack.equinecare.modules.notification.entity.EmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    // Lọc tùy chọn: tham số null thì bỏ qua điều kiện đó
    @Query("SELECT e FROM EmailLog e WHERE (:userId IS NULL OR e.userId = :userId) AND (:purpose IS NULL OR e.purpose = :purpose) "
            + "AND (:status IS NULL OR e.status = :status) AND (:toEmail IS NULL OR LOWER(e.toEmail) LIKE :toEmail) "
            + "AND (:fromTime IS NULL OR e.sentAt >= :fromTime) AND (:toTime IS NULL OR e.sentAt < :toTime) ORDER BY e.sentAt DESC, e.logId DESC")
    Page<EmailLog> search(@Param("userId") Integer userId, @Param("purpose") String purpose, @Param("status") String status,
                          @Param("toEmail") String toEmail, @Param("fromTime") LocalDateTime fromTime, @Param("toTime") LocalDateTime toTime,
                          Pageable pageable);
}
