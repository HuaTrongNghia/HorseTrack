package com.horsetrack.equinecare.modules.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssignmentRejectRequestDTO {

    @NotBlank(message = "Lý do không được để trống")
    @Size(min = 10, max = 500, message = "Lý do phải có từ 10 đến 500 ký tự")
    private String reason;
}
