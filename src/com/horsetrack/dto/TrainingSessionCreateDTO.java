package com.horsetrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TrainingSessionCreateDTO {

    @NotNull(message = "Ngày giờ buổi tập không được để trống")
    private LocalDateTime sessionDate;

    @NotBlank(message = "Loại bài tập không được để trống")
    private String activityType;

    @NotNull(message = "ID nhân viên phân công không được để trống")
    private Integer assignedToId;
}