
package com.horsetrack.equinecare.modules.training.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** HLV / Groom dừng buổi tập vì chỉ số sức khỏe vượt ngưỡng */
@Data
public class SessionStopRequestDTO {

    @NotBlank(message = "Phải mô tả dấu hiệu / lý do dừng buổi tập")
    @Size(max = 500, message = "Lý do dừng không được vượt quá 500 ký tự")
    private String reason;

    // true (mặc định): gửi ca khám cho bác sĩ, buổi tập chờ bác sĩ quyết định; false: chỉ kết thúc buổi tập
    private Boolean requestVet = true;

    @Pattern(regexp = "^(LOW|MEDIUM|HIGH|CRITICAL)$", message = "severity chỉ nhận LOW, MEDIUM, HIGH, CRITICAL")
    private String severity = "HIGH";

    @Min(value = 1, message = "Nhịp tim phải lớn hơn 0") @Max(value = 400, message = "Nhịp tim không hợp lệ")
    private Integer heartRate;

    @DecimalMin(value = "0.0", message = "Tốc độ không được âm") @DecimalMax(value = "100.0", message = "Tốc độ không hợp lệ")
    private Double speedKmh;
}
