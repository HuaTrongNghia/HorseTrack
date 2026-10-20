package com.horsetrack.equinecare.modules.inventory.service;

import com.horsetrack.equinecare.modules.inventory.dto.*;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface InventoryService {
    List<InventoryItemDTO> getItems(String category, String keyword);
    InventoryItemDTO getItemDetail(Integer itemId);
    InventoryItemDTO createItem(InventoryItemRequestDTO request);
    InventoryItemDTO updateItem(Integer itemId, InventoryItemRequestDTO request);
    InventoryItemDTO updateSafetyLevel(Integer itemId, SafetyLevelRequestDTO request);
    List<InventoryItemDTO> getLowStockItems();
    StockMovementResponseDTO recordMovement(StockMovementRequestDTO request);
    Page<StockTransactionDTO> getTransactions(Integer itemId, String txType, LocalDate fromDate, LocalDate toDate, int page, int size);
}
