package com.horsetrack.equinecare.modules.inventory.service;

import com.horsetrack.equinecare.modules.inventory.dto.InventoryItemDTO;
import com.horsetrack.equinecare.modules.inventory.dto.InventoryLotDTO;
import com.horsetrack.equinecare.modules.inventory.dto.StockTransactionDTO;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryLot;
import com.horsetrack.equinecare.modules.inventory.entity.StockTransaction;

import java.util.List;
import java.util.stream.Collectors;

/** Chuyển entity kho sang DTO, dùng chung cho các service của module kho. */
public final class InventoryMapper {

    private InventoryMapper() {
    }

    public static InventoryItemDTO toItemDTO(InventoryItem item, List<InventoryLot> lots) {
        double available = StockLedgerService.round2(item.getAvailableStock());
        double safety = item.getMinSafetyStock() != null ? item.getMinSafetyStock() : 0;
        return InventoryItemDTO.builder()
                .itemId(item.getItemId())
                .itemCode(item.getItemCode())
                .itemName(item.getItemName())
                .category(item.getCategory())
                .itemGroup(item.getItemGroup())
                .brand(item.getBrand())
                .unit(item.getUnit())
                .unitPrice(item.getUnitPrice())
                .currentStock(item.getCurrentStock())
                .heldStock(item.getHeldStock())
                .availableStock(available)
                .minSafetyStock(item.getMinSafetyStock())
                .belowSafetyLevel(available < safety)
                .route(item.getRoute())
                .isControlled(item.getIsControlled())
                .requiresPrescription(item.getRequiresPrescription())
                .withdrawalHours(item.getWithdrawalHours())
                .doseReference(item.getDoseReference())
                .note(item.getNote())
                .lastRestockedAt(item.getLastRestockedAt())
                .lots(lots == null ? null : lots.stream()
                        .map(lot -> InventoryLotDTO.builder()
                                .lotId(lot.getLotId())
                                .lotNo(lot.getLotNo())
                                .expiryDate(lot.getExpiryDate())
                                .quantity(lot.getQuantity())
                                .receivedAt(lot.getReceivedAt())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    public static StockTransactionDTO toTransactionDTO(StockTransaction tx) {
        return StockTransactionDTO.builder()
                .transactionId(tx.getTransactionId())
                .txCode(tx.getTxCode())
                .txType(tx.getTxType())
                .itemId(tx.getItem().getItemId())
                .itemCode(tx.getItem().getItemCode())
                .itemName(tx.getItem().getItemName())
                .lotNo(tx.getLot() != null ? tx.getLot().getLotNo() : null)
                .quantity(tx.getQuantity())
                .balanceAfter(tx.getBalanceAfter())
                .refType(tx.getRefType())
                .refId(tx.getRefId())
                .performedById(tx.getPerformedBy() != null ? tx.getPerformedBy().getUserId() : null)
                .performedByName(tx.getPerformedBy() != null ? tx.getPerformedBy().getFullName() : null)
                .performedAt(tx.getPerformedAt())
                .note(tx.getNote())
                .build();
    }

    /** Câu cảnh báo BR_08 khi khả dụng thấp hơn mức an toàn; null nếu vẫn an toàn. */
    public static String lowStockWarning(InventoryItem item) {
        double available = StockLedgerService.round2(item.getAvailableStock());
        double safety = item.getMinSafetyStock() != null ? item.getMinSafetyStock() : 0;
        if (available >= safety) {
            return null;
        }
        return String.format("Cảnh báo (BR_08): khả dụng của %s còn %s %s, thấp hơn mức an toàn %s.",
                item.getItemName(), StockLedgerService.fmt(available), item.getUnit(), StockLedgerService.fmt(safety));
    }
}
