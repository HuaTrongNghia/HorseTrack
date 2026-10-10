package com.horsetrack.equinecare.modules.training.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSessionLogDTO {
    private Integer sessionId;
    private LocalDateTime sessionDate;
    private String activityType;
    private String status;
    private String notes;
    private String assignedToName;
}
