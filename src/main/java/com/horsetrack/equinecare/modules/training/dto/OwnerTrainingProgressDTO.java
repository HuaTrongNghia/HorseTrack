package com.horsetrack.equinecare.modules.training.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerTrainingProgressDTO {
    private Integer planId;
    private String planName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Double progress;
    private List<TrainingSessionLogDTO> sessions;
}
