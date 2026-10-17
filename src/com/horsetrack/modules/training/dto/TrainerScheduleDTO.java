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
}
