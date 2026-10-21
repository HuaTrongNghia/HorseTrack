package com.horsetrack.equinecare.modules.horse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một mục kiểm tra hồ sơ: IDENTITY (danh tính chủ), HEALTH (giấy sức khỏe), OWNERSHIP (giấy tờ sở hữu). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationCheckDTO {

    @NotBlank(message = "checkKey là bắt buộc")
    @Pattern(regexp = "(?i)^(IDENTITY|HEALTH|OWNERSHIP)$", message = "checkKey phải là IDENTITY, HEALTH hoặc OWNERSHIP")
    private String checkKey;

    @NotNull(message = "passed là bắt buộc")
    private Boolean passed;

    @Size(max = 500, message = "detail không được vượt quá 500 ký tự")
    private String detail;

    // Chỉ có ở phản hồi
    private String checkedByName;
    private java.time.LocalDateTime checkedAt;
}
