package com.horsetrack.equinecare.modules.care.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareTaskCreateRequestDTO {

    @NotNull(message = "Phải chọn ngựa (horseId)")
    private Integer horseId;

    @NotNull(message = "Phải chọn Groom thực hiện (groomId)")
    private Integer groomId;

    @NotBlank(message = "Loại công việc (taskType) là bắt buộc")
    @Pattern(regexp = "(?i)^(FEED|CLEAN|GROOM|WALK|COOL|HOOF|MEDICATION|OTHER)$",
            message = "taskType phải là FEED, CLEAN, GROOM, WALK, COOL, HOOF, MEDICATION hoặc OTHER")
    private String taskType;

    @Size(max = 300, message = "Tiêu đề (title) không được vượt quá 300 ký tự")
    private String title;

    @NotNull(message = "Ngày thực hiện (taskDate) là bắt buộc")
    @FutureOrPresent(message = "Ngày thực hiện không được ở trong quá khứ")
    private LocalDate taskDate;

    @NotNull(message = "Giờ thực hiện (scheduledTime) là bắt buộc, định dạng HH:mm:ss")
    private LocalTime scheduledTime;

    private String instructions;
}
