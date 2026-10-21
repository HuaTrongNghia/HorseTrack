package com.horsetrack.equinecare.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplyRequestDTO {
    private Integer requisitionId;
    private String requisitionCode;
    private Integer requestedById;
    private String requestedByName;
    private String barnZone;
    private Integer itemId;
    private String itemCode;
    private String itemName;
    private String unit;
    private Double quantity;
    private Double availableStock;   // Khả dụng hiện tại của vật tư, để kho quyết định cấp hay chờ hàng
    private String reason;
    private LocalDateTime requestedAt;
    private LocalDate neededBy;
    private String status;
    private String rejectReason;
    private Double issuedQuantity;
    private String issuedByName;
    private LocalDateTime issuedAt;
    private List<String> purchaseOrderCodes; // Đơn mua hàng gắn với yêu cầu này
}
