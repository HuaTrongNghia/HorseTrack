package com.horsetrack.equinecare.modules.inventory.service;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.inventory.dto.InventoryItemDTO;
import com.horsetrack.equinecare.modules.inventory.dto.InventoryLotDTO;
import com.horsetrack.equinecare.modules.inventory.dto.StockTransactionDTO;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryLot;
import com.horsetrack.equinecare.modules.inventory.entity.StockTransaction;
import jakarta.persistence.EntityNotFoundException;

import java.util.List;
import java.util.function.Supplier;
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
        InventoryItem item = linked(() -> tx.getItem(), InventoryItem::getItemId);
        InventoryLot lot = linked(() -> tx.getLot(), InventoryLot::getLotNo);
        User user = linked(() -> tx.getPerformedBy(), User::getUserId);
        return StockTransactionDTO.builder()
                .transactionId(tx.getTransactionId())
                .txCode(tx.getTxCode())
                .txType(tx.getTxType())
                .itemId(item != null ? item.getItemId() : null)
                .itemCode(item != null ? item.getItemCode() : null)
                .itemName(item != null ? item.getItemName() : null)
                .lotNo(lot != null ? lot.getLotNo() : null)
                .quantity(tx.getQuantity())
                .balanceAfter(tx.getBalanceAfter())
                .refType(tx.getRefType())
                .refId(tx.getRefId())
                .performedById(user != null ? user.getUserId() : null)
                .performedByName(user != null ? user.getFullName() : null)
                .performedAt(tx.getPerformedAt())
                .note(tx.getNote())
                .build();
    }

    private static <T> T linked(Supplier<T> load, java.util.function.Function<T, ?> touch) {
        try {
            T value = load.get();
            if (value != null) touch.apply(value);
            return value;
        } catch (EntityNotFoundException ex) {
            return null;
        }
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
