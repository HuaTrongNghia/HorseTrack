package com.horsetrack.equinecare.modules.medical.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;
@Data
public class MedicalRecordCreateRequestDTO {
    @NotNull(message = "Horse ID is required")
    private Integer horseId;
    
    @NotNull(message = "Visit date is required")
    private LocalDate visitDate;
    
    private String symptoms;
    
    @NotBlank(message = "Diagnosis is required")
    private String diagnosis;
    
    @NotNull(message = "Is lockout ordered is required")
    private Boolean isLockoutOrdered;
    
    @Valid
    private List<InjuryMarkCreateDTO> injuryMarks;
    
    @Valid
    private List<PrescriptionItemCreateDTO> prescriptionItems;
}
