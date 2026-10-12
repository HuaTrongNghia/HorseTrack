package com.horsetrack.equinecare.modules.training.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingPlanSummaryDTO {
    private Integer planId;
    private String planName;
    private String horseName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Double progress;
}
