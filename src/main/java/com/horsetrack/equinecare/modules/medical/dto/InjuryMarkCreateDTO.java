package com.horsetrack.equinecare.modules.medical.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data
public class InjuryMarkCreateDTO {
    @NotBlank(message = "Body part code is required")
    private String bodyPartCode;
    
    @NotNull(message = "Coordinate X is required")
    private Double coordX;
    
    @NotNull(message = "Coordinate Y is required")
    private Double coordY;
    
    @NotBlank(message = "Severity level is required")
    private String severityLevel;
}
