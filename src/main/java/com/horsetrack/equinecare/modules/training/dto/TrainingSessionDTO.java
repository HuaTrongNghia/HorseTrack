package com.horsetrack.equinecare.modules.training.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSessionDTO {
    private Integer sessionId;
    private LocalDateTime sessionDate;
    private String activityType;
    private String status;
    private String notes;
    private Integer assignedToId;
    private String assignedToName;
    private Integer evaluationScore;
    private String evaluationComment;
    private String evaluatedBy;
    private String evaluatedAt;
    private Integer maxHeartRate;
    private java.math.BigDecimal maxSpeedKmh;
    private LocalDateTime startedAt;
    private LocalDateTime pausedAt;
    private String pausedReason;
    private java.util.List<SessionReadingDTO> readings;
    private java.util.List<SessionReadingDTO> recovery;
    private Integer phaseNo;   // giai đoạn giáo án chứa ngày tập (null nếu giáo án không chia giai đoạn)
}
