package com.horsetrack.equinecare.modules.notification.repository;

import com.horsetrack.equinecare.modules.notification.entity.AppNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

    Page<AppNotification> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

    long countByUserIdAndReadFalse(Integer userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE AppNotification n SET n.read = true WHERE n.notificationId = :id AND n.userId = :userId AND n.read = false")
    int markRead(@Param("id") Long id, @Param("userId") Integer userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE AppNotification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    int markAllRead(@Param("userId") Integer userId);
}
