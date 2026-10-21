package com.horsetrack.equinecare.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderDTO {
    private Integer orderId;
    private String orderCode;
    private Integer supplierId;
    private String supplierName;
    private String purpose;
    private String status;
    private LocalDate expectedDeliveryDate;
    private LocalDate previousEta;
    private LocalDate requiredBy;
    private Boolean etaLaterThanRequired; // ETA trễ hơn ngày cần hàng
    private LocalDateTime orderedAt;
    private LocalDateTime receivedAt;
    private String createdByName;
    private String note;
    private Integer protocolId;
    private Integer requisitionId;
    private BigDecimal totalAmount;
    private List<Line> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private Integer poLineId;
        private Integer itemId;
        private String itemCode;
        private String itemName;
        private String unit;
        private Double quantityOrdered;
        private Double quantityReceived;
        private Double quantityRemaining;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private String lotNo;
        private LocalDate expiryDate;
    }
}
