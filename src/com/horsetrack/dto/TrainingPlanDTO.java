package com.horsetrack.dto;
import lombok.Data;

@Data
public class TrainingPlanDTO {
    private String horseId;
    private String groomId;
    private Double distance;
    private Double weight;
    private String surfaceType;
}