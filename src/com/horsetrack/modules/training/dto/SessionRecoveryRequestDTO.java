package com.horsetrack.equinecare.modules.training.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Groom nhập nhịp tim hồi phục khi buổi tập đang tạm dừng chờ HLV */
@Data
public class SessionRecoveryRequestDTO {

    // Cùng định dạng với SessionReadingRequestDTO.time
    private String time;

    @NotNull(message = "Nhịp tim là bắt buộc")
    @Min(value = 1, message = "Nhịp tim phải lớn hơn 0") @Max(value = 300, message = "Nhịp tim tối đa 300 bpm")
    private Integer heartRate;
}
