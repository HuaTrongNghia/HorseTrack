package com.horsetrack.equinecare.modules.training.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSessionCreateRequestDTO {

    @NotNull(message = "Ngày và giờ của buổi tập là bắt buộc")
    @FutureOrPresent(message = "Ngày giờ buổi tập không được ở trong quá khứ")
    private LocalDateTime sessionDate;

    @NotBlank(message = "Loại hoạt động (Activity Type) là bắt buộc")
    private String activityType;

    private String notes;

    @NotNull(message = "Phải chọn nhân viên (Groom/Trainer) để gán việc")
    private Integer assignedToId;

    // Ngưỡng của buổi tập; bỏ trống thì dùng mặc định (app.training.default-max-heart-rate / default-max-speed-kmh)
    @jakarta.validation.constraints.Min(value = 60, message = "Ngưỡng nhịp tim tối thiểu 60 bpm")
    @jakarta.validation.constraints.Max(value = 300, message = "Ngưỡng nhịp tim tối đa 300 bpm")
    private Integer maxHeartRate;

    @jakarta.validation.constraints.DecimalMin(value = "1.0", message = "Ngưỡng tốc độ tối thiểu 1 km/h")
    @jakarta.validation.constraints.DecimalMax(value = "100.0", message = "Ngưỡng tốc độ tối đa 100 km/h")
    private java.math.BigDecimal maxSpeedKmh;
}
