package com.horsetrack.dto;

import lombok.Data;

@Data
public class TrainingStageDTO {
    private String name;
    private Integer weeks;
    private String intensity;
    private Integer distanceM;
    private Integer sessionsPerWeek;
    private Integer targetBpm;
}