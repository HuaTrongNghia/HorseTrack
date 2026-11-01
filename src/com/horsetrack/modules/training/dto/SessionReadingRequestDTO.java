package com.horsetrack.equinecare.modules.training.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/** Groom nhập một lần đo trong buổi tập */
@Data
public class SessionReadingRequestDTO {

    // "HH:mm", "HH:mm:ss" (tính theo ngày của buổi tập) hoặc ISO "yyyy-MM-ddTHH:mm"; bỏ trống = bây giờ
    private String time;

    @NotNull(message = "Nhịp tim là bắt buộc")
    @Min(value = 1, message = "Nhịp tim phải lớn hơn 0") @Max(value = 300, message = "Nhịp tim tối đa 300 bpm")
    private Integer heartRate;

    @NotNull(message = "Tốc độ là bắt buộc")
    @DecimalMin(value = "0.0", message = "Tốc độ không được âm") @DecimalMax(value = "100.0", message = "Tốc độ tối đa 100 km/h")
    private BigDecimal speedKmh;
}
