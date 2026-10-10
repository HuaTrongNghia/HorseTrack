package com.horsetrack.equinecare.modules.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionExecuteRequestDTO {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(COMPLETED|CANCELLED)$", message = "Status must be either COMPLETED or CANCELLED")
    private String status;

    private String notes;
}
