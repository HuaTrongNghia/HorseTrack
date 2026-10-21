package com.horsetrack.equinecare.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Tạo hoặc cập nhật phiếu kiểm kê (khi còn DRAFT). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockCountRequestDTO {

    @NotBlank(message = "Tháng kiểm kê (countMonth) là bắt buộc, định dạng yyyy-MM")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "countMonth phải có dạng yyyy-MM, ví dụ 2026-10")
    private String countMonth;

    @Size(max = 500, message = "Ghi chú (note) không được vượt quá 500 ký tự")
    private String note;

    @NotEmpty(message = "Phiếu kiểm kê phải có ít nhất một dòng (lines)")
    @Valid
    private List<Line> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        @NotNull(message = "Phải chọn vật tư (itemId)")
        private Integer itemId;

        @NotNull(message = "Số lượng đếm thực tế (countedQty) là bắt buộc")
        @DecimalMin(value = "0", message = "Số lượng đếm không được âm")
        @Digits(integer = 8, fraction = 2, message = "Số lượng tối đa 2 chữ số thập phân")
        private Double countedQty;

        // Bắt buộc khi số đếm khác số trên hệ thống (kiểm tra lúc chốt phiếu)
        @Size(max = 255, message = "Lý do chênh lệch (reason) không được vượt quá 255 ký tự")
        private String reason;
    }
}
