package com.horsetrack.equinecare.modules.training.dto;

import com.horsetrack.equinecare.modules.training.dto.TrainingPlanPhaseDTO;
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
    private String delayNote;
    private List<TrainingPlanPhaseDTO> phases;
    private List<TrainingSessionLogDTO> sessions;
}
