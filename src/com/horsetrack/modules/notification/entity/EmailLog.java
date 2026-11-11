package com.horsetrack.equinecare.modules.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Nhật ký một lần gửi email (F1) */
@Entity
@Table(name = "Email_Logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailLog {

    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    /** Chế độ dev (chưa cấu hình MAIL_USERNAME): không gửi thật */
    public static final String SKIPPED = "SKIPPED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "to_email", nullable = false, length = 255)
    private String toEmail;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "purpose", nullable = false, length = 40)
    private String purpose;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "error", length = 1000)
    private String error;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
