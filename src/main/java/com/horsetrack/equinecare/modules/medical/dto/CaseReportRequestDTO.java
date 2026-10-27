package com.horsetrack.equinecare.modules.medical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Groom / Trainer báo sự cố sức khỏe của ngựa; ca được gửi vào hộp thư của bác sĩ thú y. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseReportRequestDTO {

    @NotNull(message = "Phải chọn ngựa (horseId)")
    private Integer horseId;

    @NotBlank(message = "Loại sự cố (caseType) là bắt buộc")
    @Size(max = 50, message = "Loại sự cố không được vượt quá 50 ký tự")
    private String caseType;

    // Bỏ trống thì mặc định MEDIUM
    @Pattern(regexp = "(?i)^(LOW|MEDIUM|HIGH|CRITICAL)$", message = "severity phải là LOW, MEDIUM, HIGH hoặc CRITICAL")
    private String severity;

    @NotBlank(message = "Mô tả triệu chứng (description) là bắt buộc")
    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    private String description;
}
