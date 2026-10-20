package com.horsetrack.equinecare.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponseDTO {
    private StockTransactionDTO transaction;
    private InventoryItemDTO item;     // Tồn kho của vật tư sau phát sinh
    private String lowStockWarning;    // Có giá trị khi khả dụng thấp hơn mức an toàn (BR_08)
}
