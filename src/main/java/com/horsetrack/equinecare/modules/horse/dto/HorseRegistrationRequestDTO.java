package com.horsetrack.equinecare.modules.horse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorseRegistrationRequestDTO {

    @NotBlank(message = "Horse name is required")
    private String horseName;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dob;

    @NotBlank(message = "Gender is required")
    @Pattern(regexp = "^(Colt|Filly|Stallion|Mare|Gelding)$", message = "Gender must be one of: Colt, Filly, Stallion, Mare, Gelding")
    private String gender;

    private String breed;

    private String pedigree;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.1", message = "Weight must be greater than or equal to 0.1")
    private BigDecimal weightKg;

    @NotNull(message = "Owner ID is required")
    private Integer ownerId;
}
