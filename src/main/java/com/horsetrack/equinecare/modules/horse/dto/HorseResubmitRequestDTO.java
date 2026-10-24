package com.horsetrack.equinecare.modules.horse.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Chủ ngựa sửa thông tin và nộp lại hồ sơ bị từ chối. Field nào gửi lên mới được cập nhật. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorseResubmitRequestDTO {

    @DecimalMin(value = "0.1", message = "Cân nặng phải lớn hơn hoặc bằng 0.1 kg")
    private BigDecimal weightKg;

    @Size(max = 255, message = "Phả hệ (pedigree) không được vượt quá 255 ký tự")
    private String pedigree;

    @Size(max = 50, message = "Màu lông (color) không được vượt quá 50 ký tự")
    private String color;

    // Chiều cao đến vai theo cm (120-205 cm)
    @DecimalMin(value = "120.0", message = "Chiều cao (heightCm) từ 120 cm")
    @DecimalMax(value = "205.0", message = "Chiều cao (heightCm) không vượt quá 205 cm")
    @Digits(integer = 3, fraction = 1, message = "Chiều cao tối đa 1 chữ số thập phân")
    private BigDecimal heightCm;

    /** @deprecated chỉ để tương thích FE cũ, dùng heightCm. Gửi kèm thì đổi sang cm (1 hand = 10.16 cm). */
    @Deprecated
    @DecimalMin(value = "1.0", message = "Chiều cao (heightHands) phải từ 1.0 hand")
    @DecimalMax(value = "25.0", message = "Chiều cao (heightHands) không vượt quá 25.0 hand")
    private BigDecimal heightHands;

    // Ghi chú của chủ ngựa: đã sửa những gì
    @Size(max = 255, message = "Ghi chú (note) không được vượt quá 255 ký tự")
    private String note;
}
