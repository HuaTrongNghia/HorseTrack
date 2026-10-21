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

    @DecimalMin(value = "1.0", message = "Chiều cao (heightHands) phải từ 1.0 hand")
    @DecimalMax(value = "25.0", message = "Chiều cao (heightHands) không vượt quá 25.0 hand")
    @Digits(integer = 3, fraction = 1, message = "Chiều cao tối đa 1 chữ số thập phân")
    private BigDecimal heightHands;

    @Size(max = 50, message = "Số hộ chiếu (passportNo) không được vượt quá 50 ký tự")
    private String passportNo;

    @Size(max = 150, message = "Nơi cấp giấy chứng nhận sức khỏe không được vượt quá 150 ký tự")
    private String healthCertIssuer;

    private LocalDate healthCertExpiry;

    // Ghi chú của chủ ngựa: đã sửa những gì
    @Size(max = 255, message = "Ghi chú (note) không được vượt quá 255 ký tự")
    private String note;
}
