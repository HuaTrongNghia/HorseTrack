package com.horsetrack.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VetActionRequestDTO {
    @NotBlank(message = "Mã ca khám không được để trống")
    private String caseId;
    
    @NotBlank(message = "Vui lòng nhập kết quả khám")
    private String vetNotes;     
    
    private String treatmentDirection; 
}
