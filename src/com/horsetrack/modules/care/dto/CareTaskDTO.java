package com.horsetrack.equinecare.modules.care.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CareTaskDTO {
    private Integer taskId;
    private String taskType;
    private String status;
    private LocalDateTime dueDate;
    private String horseName;
    private String stallZone;
}
