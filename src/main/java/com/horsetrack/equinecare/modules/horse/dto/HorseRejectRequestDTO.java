package com.horsetrack.equinecare.modules.horse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Club Manager từ chối hồ sơ ngựa (SRS: lý do tối thiểu 10 ký tự, có thể cho phép nộp lại). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorseRejectRequestDTO {

    @NotBlank(message = "Lý do từ chối (reason) là bắt buộc")
    @Size(min = 10, max = 500, message = "Lý do từ chối phải dài 10–500 ký tự")
    private String reason;

    // true: chủ ngựa được sửa và nộp lại hồ sơ
    private Boolean allowResubmit;

    // Checklist kiểm tra (tùy chọn) để ghi lại mục nào chưa đạt
    @Valid
    private List<RegistrationCheckDTO> checks;
}
