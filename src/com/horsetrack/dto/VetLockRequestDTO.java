package com.horsetrack.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class VetLockRequestDTO {

    @NotNull(message = "ID ngựa không được để trống")
    private Integer horseId;

    @NotBlank(message = "Vui lòng nhập lý do khóa y tế")
    private String reason; // Lý do khóa (VD: Nhịp tim vượt mức 180bpm)

    // ID của bác sĩ thực hiện khóa (Tạm truyền từ UI xuống, sau này có thể lấy từ Token)
    private Integer vetId;
}