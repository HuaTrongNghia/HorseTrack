package com.horsetrack.dto;
import lombok.Data;

@Data
public class TrainingMetricDTO {
    private String sessionId;
    private Integer heartRate;
    private Double speed;
}