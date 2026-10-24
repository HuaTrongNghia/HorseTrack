package com.horsetrack.equinecare.modules.horse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorseRegistrationRequestDTO {

    @NotBlank(message = "Horse name is required")
    private String horseName;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dob;

    @NotBlank(message = "Gender is required")
    @Pattern(regexp = "^(Colt|Filly|Stallion|Mare|Gelding)$", message = "Gender must be one of: Colt, Filly, Stallion, Mare, Gelding")
    private String gender;

    private String breed;

    private String pedigree;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.1", message = "Weight must be greater than or equal to 0.1")
    private BigDecimal weightKg;

    // ----- Thông tin hồ sơ (tùy chọn), lưu vào các cột sẵn có của bảng Horses -----
    @jakarta.validation.constraints.Size(max = 50, message = "Màu lông (color) không được vượt quá 50 ký tự")
    private String color;

    // Chiều cao đến vai theo cm (120-205 cm)
    @DecimalMin(value = "120.0", message = "Chiều cao (heightCm) từ 120 cm")
    @jakarta.validation.constraints.DecimalMax(value = "205.0", message = "Chiều cao (heightCm) không vượt quá 205 cm")
    @jakarta.validation.constraints.Digits(integer = 3, fraction = 1, message = "Chiều cao tối đa 1 chữ số thập phân")
    private BigDecimal heightCm;

    /** @deprecated chỉ để tương thích FE cũ, dùng heightCm. Gửi kèm thì đổi sang cm (1 hand = 10.16 cm). */
    @Deprecated
    @DecimalMin(value = "1.0", message = "Chiều cao (heightHands) phải từ 1.0 hand")
    @jakarta.validation.constraints.DecimalMax(value = "25.0", message = "Chiều cao (heightHands) không vượt quá 25.0 hand")
    private BigDecimal heightHands;

    // Ghi chú của chủ ngựa khi nộp hồ sơ
    @jakarta.validation.constraints.Size(max = 255, message = "Ghi chú (note) không được vượt quá 255 ký tự")
    private String note;

    // Chủ ngựa đăng ký: bỏ trống (lấy tài khoản đang đăng nhập, số điện thoại lấy từ hồ sơ). Club Manager đăng ký hộ: bắt buộc.
    private Integer ownerId;
}
