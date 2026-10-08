package com.horsetrack.dto;
import lombok.Data;

@Data
public class EvaluationDTO {
    private String sessionId;
    private Integer score;
    private String comments;
}