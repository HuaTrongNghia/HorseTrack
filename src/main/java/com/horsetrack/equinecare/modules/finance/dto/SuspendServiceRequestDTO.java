package com.horsetrack.equinecare.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SuspendServiceRequestDTO {

    @NotBlank(message = "Phải nhập lý do đình chỉ")
    @Size(max = 255, message = "Lý do đình chỉ không được vượt quá 255 ký tự")
    private String reason;
}
