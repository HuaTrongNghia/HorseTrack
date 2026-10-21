package com.horsetrack.equinecare.modules.medical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProtocolDTO {
    private String protocolCode;
    private String horseName;
    private String diagnosis;
    private String caseCode;
    private String requiredDate;
    private String estimatedCost;
    private String status;
    private String nextStep;
}
