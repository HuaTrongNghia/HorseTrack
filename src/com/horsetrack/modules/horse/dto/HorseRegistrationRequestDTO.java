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

    @DecimalMin(value = "1.0", message = "Chiều cao (heightHands) phải từ 1.0 hand")
    @jakarta.validation.constraints.DecimalMax(value = "25.0", message = "Chiều cao (heightHands) không vượt quá 25.0 hand")
    @jakarta.validation.constraints.Digits(integer = 3, fraction = 1, message = "Chiều cao tối đa 1 chữ số thập phân")
    private BigDecimal heightHands;

    @jakarta.validation.constraints.Size(max = 50, message = "Số hộ chiếu (passportNo) không được vượt quá 50 ký tự")
    private String passportNo;

    @jakarta.validation.constraints.Size(max = 150, message = "Nơi cấp giấy chứng nhận sức khỏe không được vượt quá 150 ký tự")
    private String healthCertIssuer;

    private LocalDate healthCertExpiry;

    // Ghi chú của chủ ngựa khi nộp hồ sơ
    @jakarta.validation.constraints.Size(max = 255, message = "Ghi chú (note) không được vượt quá 255 ký tự")
    private String note;

    @NotNull(message = "Owner ID is required")
    private Integer ownerId;
}
