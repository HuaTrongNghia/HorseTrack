package com.horsetrack.equinecare.modules.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Nhập kho (RECEIVE), xuất kho (ISSUE), điều chỉnh sau kiểm đếm (ADJUST) hoặc hủy hàng hỏng/hết hạn (DISPOSE).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementRequestDTO {

    @NotNull(message = "Phải chọn vật tư (itemId)")
    private Integer itemId;

    @NotBlank(message = "Loại phát sinh (movementType) là bắt buộc")
    @Pattern(regexp = "(?i)^(RECEIVE|ISSUE|ADJUST|DISPOSE)$",
            message = "movementType phải là RECEIVE, ISSUE, ADJUST hoặc DISPOSE")
    private String movementType;

    // RECEIVE/ISSUE/DISPOSE: số dương. ADJUST: số dương để tăng, số âm để giảm.
    @NotNull(message = "Số lượng (quantity) là bắt buộc")
    @Digits(integer = 8, fraction = 2, message = "Số lượng tối đa 2 chữ số thập phân")
    private Double quantity;

    // Bắt buộc khi RECEIVE; tùy chọn với các loại còn lại (nếu có thì trừ/cộng đúng lô đó)
    @Size(max = 50, message = "Số lô (lotNo) không được vượt quá 50 ký tự")
    private String lotNo;

    // Chỉ dùng khi RECEIVE một lô mới
    private LocalDate expiryDate;

    // Lý do; bắt buộc khi ADJUST hoặc DISPOSE
    @Size(max = 500, message = "Ghi chú (note) không được vượt quá 500 ký tự")
    private String note;
}
