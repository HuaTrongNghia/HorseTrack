package com.horsetrack.equinecare.modules.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Thông báo in-app (chuông) — luôn gắn đúng userId, không broadcast theo role. */
@Entity
@Table(name = "App_Notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "purpose", nullable = false, length = 40)
    private String purpose;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    /** Đường dẫn FE (vd. /trainer, /owner/invoices) — tùy chọn. */
    @Column(name = "link", length = 255)
    private String link;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
