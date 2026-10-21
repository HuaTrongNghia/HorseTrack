package com.horsetrack.equinecare.modules.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Dùng chung cho thêm mới và cập nhật thông tin vật tư.
 * Tồn kho không sửa ở đây mà phải qua nhập/xuất/điều chỉnh để có nhật ký kho.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemRequestDTO {

    @NotBlank(message = "Tên vật tư (itemName) là bắt buộc")
    @Size(max = 100, message = "Tên vật tư không được vượt quá 100 ký tự")
    private String itemName;

    @NotBlank(message = "Nhóm vật tư (category) là bắt buộc")
    @Pattern(regexp = "(?i)^(FEED|SUPPLEMENT|MEDICINE|VACCINE|MEDICAL_SUPPLY|STABLE_SUPPLY|EQUIPMENT|OTHER)$",
            message = "category phải là FEED, SUPPLEMENT, MEDICINE, VACCINE, MEDICAL_SUPPLY, STABLE_SUPPLY, EQUIPMENT hoặc OTHER")
    private String category;

    @NotBlank(message = "Đơn vị tính (unit) là bắt buộc")
    @Size(max = 20, message = "Đơn vị tính không được vượt quá 20 ký tự")
    private String unit;

    @NotNull(message = "Đơn giá (unitPrice) là bắt buộc")
    @DecimalMin(value = "0", message = "Đơn giá không được âm")
    @Digits(integer = 10, fraction = 2, message = "Đơn giá tối đa 10 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal unitPrice;

    @NotNull(message = "Mức tồn kho an toàn (minSafetyStock) là bắt buộc")
    @DecimalMin(value = "0", message = "Mức tồn kho an toàn không được âm")
    @Digits(integer = 8, fraction = 2, message = "Mức tồn kho an toàn tối đa 2 chữ số thập phân")
    private Double minSafetyStock;

    @Size(max = 50, message = "Nhóm phụ (itemGroup) không được vượt quá 50 ký tự")
    private String itemGroup;

    @Size(max = 100, message = "Nhãn hiệu (brand) không được vượt quá 100 ký tự")
    private String brand;

    @Size(max = 50, message = "Đường dùng (route) không được vượt quá 50 ký tự")
    private String route;

    private Boolean isControlled;

    private Boolean requiresPrescription;

    @Min(value = 0, message = "Thời gian ngưng thuốc (withdrawalHours) không được âm")
    private Integer withdrawalHours;

    @Size(max = 255, message = "Liều tham khảo (doseReference) không được vượt quá 255 ký tự")
    private String doseReference;

    @Size(max = 500, message = "Ghi chú (note) không được vượt quá 500 ký tự")
    private String note;
}
