package com.horsetrack.equinecare.modules.training.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Giai đoạn giáo án: dùng cho cả request (POST /training-plans) và response */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingPlanPhaseDTO {

    // Response: thứ tự giai đoạn (request bỏ qua, đánh số theo thứ tự gửi lên)
    private Integer phaseNo;

    @NotBlank(message = "Tên giai đoạn (name) là bắt buộc")
    @Size(max = 100, message = "Tên giai đoạn tối đa 100 ký tự")
    private String name;

    @NotNull(message = "Số tuần của giai đoạn (weeks) là bắt buộc")
    @Min(value = 1, message = "Giai đoạn tối thiểu 1 tuần") @Max(value = 52, message = "Giai đoạn tối đa 52 tuần")
    private Integer weeks;

    // LOW / MEDIUM / HIGH (hoặc Thấp / Trung bình / Cao)
    @NotBlank(message = "Cường độ (intensity) là bắt buộc")
    private String intensity;

    @DecimalMin(value = "0", message = "Cự ly không được âm") @DecimalMax(value = "20000", message = "Cự ly tối đa 20000 m")
    private BigDecimal distanceM;

    @DecimalMin(value = "0", message = "Tốc độ không được âm") @DecimalMax(value = "100", message = "Tốc độ tối đa 100 km/h")
    private BigDecimal targetSpeedKmh;

    // SYNTHETIC / DIRT / TURF (tùy chọn)
    private String trackType;

    @Min(value = 1, message = "Số buổi/tuần từ 1 đến 14") @Max(value = 14, message = "Số buổi/tuần từ 1 đến 14")
    private Integer sessionsPerWeek;

    // Ngưỡng nhịp tim của giai đoạn; buổi tập không đặt ngưỡng riêng sẽ dùng giá trị này
    @Min(value = 40, message = "Ngưỡng nhịp tim từ 40 đến 260 bpm") @Max(value = 260, message = "Ngưỡng nhịp tim từ 40 đến 260 bpm")
    private Integer maxHeartRate;

    // Response: ngày bắt đầu / kết thúc tính từ ngày bắt đầu giáo án và số tuần các giai đoạn trước
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean current;
}
