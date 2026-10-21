package com.horsetrack.equinecare.modules.medical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateScheduleRequestDTO {
    private List<Integer> horseIds;
    private String medicineName;
    private String scheduledDate;
    private String notes;
}
