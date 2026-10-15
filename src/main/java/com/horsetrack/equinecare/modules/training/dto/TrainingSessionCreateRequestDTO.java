package com.horsetrack.equinecare.modules.training.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainingSessionCreateRequestDTO {

    @NotNull(message = "Ngày và giờ của buổi tập là bắt buộc")
    @FutureOrPresent(message = "Ngày giờ buổi tập không được ở trong quá khứ")
    private LocalDateTime sessionDate;

    @NotBlank(message = "Loại hoạt động (Activity Type) là bắt buộc")
    private String activityType;

    private String notes;

    @NotNull(message = "Phải chọn nhân viên (Groom/Trainer) để gán việc")
    private Integer assignedToId;
}