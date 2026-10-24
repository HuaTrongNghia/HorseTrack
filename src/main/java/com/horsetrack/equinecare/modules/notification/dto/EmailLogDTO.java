package com.horsetrack.equinecare.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLogDTO {
    private Long logId;
    private String toEmail;
    private Integer userId;
    private String userFullName;
    private String purpose;
    private String subject;
    /** SENT, FAILED, SKIPPED (chế độ dev, không gửi thật) */
    private String status;
    private String error;
    private LocalDateTime sentAt;
}
