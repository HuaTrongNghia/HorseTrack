package com.horsetrack.equinecare.modules.medical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HealthStatusUpdateDTO {

    // HEALTHY / WATCH / INJURED / QUARANTINE (hoặc nhãn tiếng Việt)
    @NotBlank(message = "Phải chọn trạng thái sức khỏe")
    private String status;

    @NotBlank(message = "Phải ghi lý do đổi trạng thái") @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
