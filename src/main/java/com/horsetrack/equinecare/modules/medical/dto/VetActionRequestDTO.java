package com.horsetrack.equinecare.modules.medical.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VetActionRequestDTO {
    @NotBlank(message = "Mã ca khám không được để trống")
    private String caseId;
    
    @NotBlank(message = "Vui lòng nhập kết quả khám")
    private String vetNotes;     
    
    private String treatmentDirection; 
}
