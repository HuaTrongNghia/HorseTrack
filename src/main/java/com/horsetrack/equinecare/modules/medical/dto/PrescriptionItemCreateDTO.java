package com.horsetrack.equinecare.modules.medical.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data
public class PrescriptionItemCreateDTO {
    @NotNull(message = "Item ID is required")
    private Integer itemId;
    
    @NotBlank(message = "Dosage is required")
    private String dosage;
    
    @NotNull(message = "Quantity prescribed is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantityPrescribed;
}
