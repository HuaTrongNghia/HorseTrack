package com.horsetrack.equinecare.modules.training.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingPlanDetailDTO {
    private Integer planId;
    private String planName;
    private Integer horseId;
    private String horseName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String goal;
    private String type;
    private String status;
    private java.util.List<TrainingPlanPhaseDTO> phases;
    private List<TrainingSessionDTO> sessions;
}
