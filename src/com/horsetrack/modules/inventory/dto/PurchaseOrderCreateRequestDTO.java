package com.horsetrack.equinecare.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderCreateRequestDTO {

    @NotNull(message = "Phải chọn nhà cung cấp (supplierId)")
    private Integer supplierId;

    // RESTOCK: bổ sung kho; REQUISITION: mua cho yêu cầu vật tư của Groom; PROTOCOL: mua thuốc còn thiếu cho phác đồ
    @NotBlank(message = "Mục đích (purpose) là bắt buộc")
    @Pattern(regexp = "(?i)^(RESTOCK|REQUISITION|PROTOCOL)$", message = "purpose phải là RESTOCK, REQUISITION hoặc PROTOCOL")
    private String purpose;

    // Bắt buộc khi purpose = REQUISITION
    private Integer requisitionId;

    // Bắt buộc khi purpose = PROTOCOL
    private Integer protocolId;

    @NotNull(message = "Ngày dự kiến hàng về (expectedDeliveryDate) là bắt buộc")
    @FutureOrPresent(message = "Ngày dự kiến hàng về không được ở trong quá khứ")
    private LocalDate expectedDeliveryDate;

    // Ngày cần có hàng (tùy chọn). ETA trễ hơn ngày này thì đơn bị đánh dấu DELAYED.
    private LocalDate requiredBy;

    @Size(max = 500, message = "Ghi chú (note) không được vượt quá 500 ký tự")
    private String note;

    @NotEmpty(message = "Đơn mua phải có ít nhất một dòng vật tư (lines)")
    @Valid
    private List<Line> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        @NotNull(message = "Phải chọn vật tư (itemId)")
        private Integer itemId;

        @NotNull(message = "Số lượng đặt (quantity) là bắt buộc")
        @Positive(message = "Số lượng đặt phải lớn hơn 0")
        @Digits(integer = 8, fraction = 2, message = "Số lượng tối đa 2 chữ số thập phân")
        private Double quantity;

        // Bỏ trống thì lấy đơn giá hiện tại của vật tư
        @DecimalMin(value = "0", message = "Đơn giá không được âm")
        @Digits(integer = 10, fraction = 2, message = "Đơn giá tối đa 2 chữ số thập phân")
        private BigDecimal unitPrice;
    }
}
