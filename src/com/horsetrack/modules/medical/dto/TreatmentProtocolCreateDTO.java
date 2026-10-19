package com.horsetrack.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class TreatmentProtocolCreateDTO {
    private Integer horseId;
    private String caseId; 
    private String diagnosis; 
    private LocalDate targetDate; 
    
    private List<StageDTO> stages;

    @Data
    public static class StageDTO {
        private String stageName; 
        private LocalDate startDate;
        private Integer durationDays;
        private String goal;
        private List<ItemDTO> items; 
    }

    @Data
    public static class ItemDTO {
        private Integer medicationId;
        private Integer quantity; 
    }
}
