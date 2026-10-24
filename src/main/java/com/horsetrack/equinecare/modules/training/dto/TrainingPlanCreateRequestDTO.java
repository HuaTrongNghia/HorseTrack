package com.horsetrack.equinecare.modules.training.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingPlanCreateRequestDTO {

    @NotNull(message = "Phải chọn ngựa (horseId) cho giáo án")
    private Integer horseId;

    @NotBlank(message = "Tên giáo án (planName) là bắt buộc")
    @Size(max = 150, message = "Tên giáo án không được vượt quá 150 ký tự")
    private String planName;

    @NotNull(message = "Ngày bắt đầu (startDate) là bắt buộc")
    @FutureOrPresent(message = "Ngày bắt đầu không được ở trong quá khứ")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc (endDate) là bắt buộc")
    private LocalDate endDate;

    @Size(max = 500, message = "Mục tiêu (goal) không được vượt quá 500 ký tự")
    private String goal;

    // Loại giáo án, ví dụ "Duy trì", "Chuẩn bị thi đấu" (không cần ghi vào đầu goal nữa)
    @Size(max = 50, message = "Loại giáo án (type) không được vượt quá 50 ký tự")
    private String type;

    // Các giai đoạn theo thứ tự; tổng số tuần không vượt thời gian giáo án
    @jakarta.validation.Valid
    @Size(max = 12, message = "Giáo án tối đa 12 giai đoạn")
    private java.util.List<TrainingPlanPhaseDTO> phases;
}
