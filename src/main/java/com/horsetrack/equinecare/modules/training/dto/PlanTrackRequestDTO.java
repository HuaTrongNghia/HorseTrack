package com.horsetrack.equinecare.modules.training.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Quản lý CLB gán loại đường cho giáo án cũ: SYNTHETIC (Cỏ nhân tạo) / DIRT (Đường đất) / TURF (Đường cỏ) */
@Data
public class PlanTrackRequestDTO {

    @NotBlank(message = "Phải chọn loại đường tập: Cỏ nhân tạo, Đường đất hoặc Đường cỏ.")
    private String trackType;
}
