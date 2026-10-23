package com.horsetrack.equinecare.modules.medical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InjuryDTO {
    private Integer injuryId;
    private String part;
    /** MINOR / MODERATE / SEVERE */
    private String severity;
    /** Nhẹ / Trung bình / Nặng */
    private String severityLabel;
    private String note;
    private Double x;
    private Double y;
    private Integer caseId;
    private String caseCode;
    private Integer recordId;
    private String createdByName;
    private LocalDateTime createdAt;
}
