package com.horsetrack.equinecare.modules.care.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareTaskDTO {
    private Integer taskId;
    private String taskCode;
    private String taskType;
    private String title;
    private String status;
    private LocalDate taskDate;
    private LocalTime scheduledTime;
    private String instructions;
    private LocalDateTime completedAt;
    private Integer horseId;
    private String horseName;
    private String stallCode;
    private String stallZone;
    private Integer groomId;
    private String groomName;
}
