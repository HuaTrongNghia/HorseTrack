package com.horsetrack.equinecare.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Kho kiểm hàng cho phác đồ. Hệ thống tự tính đủ/thiếu; kho chỉ cần báo ngày về (và đơn mua) cho vật tư thiếu.
 * Toàn bộ body là tùy chọn.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockCheckRequestDTO {

    @Valid
    private List<Shortage> shortages;

    @Size(max = 500, message = "Ghi chú (note) không được vượt quá 500 ký tự")
    private String note;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Shortage {
        @NotNull(message = "Phải chọn vật tư (itemId)")
        private Integer itemId;

        // Ngày dự kiến có hàng; bỏ trống thì lấy ngày về của đơn mua (nếu có)
        private LocalDate eta;

        // Đơn mua hàng đang chờ cho vật tư này (tùy chọn)
        private Integer purchaseOrderId;
    }
}
