package com.horsetrack.equinecare.modules.medical.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** Bác sĩ đánh dấu chấn thương trên sơ đồ 2D của ngựa */
@Data
public class InjuryCreateDTO {

    @NotBlank(message = "Phải ghi vị trí chấn thương (part)") @Size(max = 100, message = "Vị trí tối đa 100 ký tự")
    private String part;

    // MINOR / MODERATE / SEVERE hoặc Nhẹ / Trung bình / Nặng
    @NotBlank(message = "Phải chọn mức độ (severity)")
    private String severity;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String note;

    // Tọa độ trên sơ đồ, tính theo phần trăm 0-100
    @NotNull(message = "Thiếu tọa độ x") @DecimalMin(value = "0", message = "x từ 0 đến 100") @DecimalMax(value = "100", message = "x từ 0 đến 100")
    private Double x;

    @NotNull(message = "Thiếu tọa độ y") @DecimalMin(value = "0", message = "y từ 0 đến 100") @DecimalMax(value = "100", message = "y từ 0 đến 100")
    private Double y;

    // ID ca khám (12) hoặc mã ca (C-0012), tùy chọn
    private String caseId;
}
