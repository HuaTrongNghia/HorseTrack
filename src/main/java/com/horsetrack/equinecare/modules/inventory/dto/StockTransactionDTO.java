package com.horsetrack.equinecare.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransactionDTO {
    private Long transactionId;
    private String txCode;
    private String txType;
    private Integer itemId;
    private String itemCode;
    private String itemName;
    private String lotNo;
    private Double quantity;
    private Double balanceAfter;
    private String refType;
    private Integer refId;
    private Integer performedById;
    private String performedByName;
    private LocalDateTime performedAt;
    private String note;
}
