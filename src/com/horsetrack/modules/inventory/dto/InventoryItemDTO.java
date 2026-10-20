package com.horsetrack.equinecare.modules.inventory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InventoryItemDTO {
    private Integer itemId;
    private String itemCode;
    private String itemName;
    private String category;
    private String itemGroup;
    private String brand;
    private String unit;
    private BigDecimal unitPrice;
    private Double currentStock;     // Đang có trong kho
    private Double heldStock;        // Đang giữ cho phác đồ điều trị
    private Double availableStock;   // Khả dụng = đang có - đang giữ
    private Double minSafetyStock;   // Mức an toàn (BR_08)
    private Boolean belowSafetyLevel;
    private String route;
    private Boolean isControlled;
    private Boolean requiresPrescription;
    private Integer withdrawalHours;
    private String doseReference;
    private String note;
    private LocalDateTime lastRestockedAt;
    private List<InventoryLotDTO> lots; // Chỉ trả về ở API xem chi tiết
}
