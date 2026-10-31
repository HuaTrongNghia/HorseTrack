package com.horsetrack.equinecare.modules.medical.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TreatmentProtocolDTO {
    private Integer protocolId;
    private String protocolCode;
    private Integer horseId;
    private String horseName;
    private Integer vetId;
    private String vetName;
    private Integer caseId;
    private Integer recordId;
    private Integer versionNo;
    private Integer replacesProtocolId;   // Phiên bản trước
    private String replacesProtocolCode;
    private Integer replacedByProtocolId; // Phiên bản sau (nếu đã bị thay thế)
    private String replacedByProtocolCode;
    private String diagnosis;
    private String state;
   
    }

    

}
