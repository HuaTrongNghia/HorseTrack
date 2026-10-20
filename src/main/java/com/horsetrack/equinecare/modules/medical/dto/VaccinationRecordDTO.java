package com.horsetrack.equinecare.modules.medical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaccinationRecordDTO {
    private String recordCode;
    private String horseName;
    private String typeName;
    private String category;
    private String expectedDate;
    private String countdown;
    private Integer cycleMonths;
    private String status;
}
