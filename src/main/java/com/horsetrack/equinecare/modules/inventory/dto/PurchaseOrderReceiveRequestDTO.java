package com.horsetrack.equinecare.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Nhận hàng của đơn mua và nhập kho. Có thể nhận một phần, nhận nhiều lần. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderReceiveRequestDTO {

    @NotEmpty(message = "Phải có ít nhất một dòng nhận hàng (lines)")
    @Valid
    private List<Line> lines;

    @Size(max = 500, message = "Ghi chú (note) không được vượt quá 500 ký tự")
    private String note;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        @NotNull(message = "Phải chọn dòng đơn hàng (poLineId)")
        private Integer poLineId;

        @NotNull(message = "Số lượng nhận (quantityReceived) là bắt buộc")
        @Positive(message = "Số lượng nhận phải lớn hơn 0")
        @Digits(integer = 8, fraction = 2, message = "Số lượng tối đa 2 chữ số thập phân")
        private Double quantityReceived;

        // Tùy chọn: để trống thì hệ thống tự sinh số lô
        @Size(max = 50, message = "Số lô không được vượt quá 50 ký tự")
        private String lotNo;

        private LocalDate expiryDate;
    }
}
