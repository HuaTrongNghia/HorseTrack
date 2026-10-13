package com.horsetrack.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Data
public class TrainingPlanCreateRequestDTO {

    @NotBlank(message = "Tên giáo án không được để trống")
    private String planName;

    @NotNull(message = "Mã ngựa không được để trống")
    private Integer horseId;

    // SỬA Ở ĐÂY: Thêm JsonFormat để Spring Boot hiểu được chuỗi ngày tháng gửi từ UI
    @NotNull(message = "Ngày bắt đầu không được để trống")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotBlank(message = "Loại giáo án không được để trống")
    private String planType;

    @NotBlank(message = "Mục tiêu không được để trống")
    private String goal;

    private List<TrainingStageDTO> stages;
}