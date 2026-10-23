package com.horsetrack.equinecare.modules.training.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainerScheduleDTO {
    private Integer sessionId;
    private LocalDateTime sessionDate;
    private String activityType;
    private String status;
    private String planName;
    private String horseName;
    private String stallZone;
    private Integer evaluationScore;
    private String evaluationComment;
    private String evaluatedBy;
    private String evaluatedAt;
    private String notes;
    private Integer maxHeartRate;
    private java.math.BigDecimal maxSpeedKmh;
    private LocalDateTime startedAt;
    private LocalDateTime pausedAt;
    private String pausedReason;
    private java.util.List<SessionReadingDTO> readings;
    private java.util.List<SessionReadingDTO> recovery;
}
