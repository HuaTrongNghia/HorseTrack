package com.horsetrack.equinecare.modules.inventory.service;

import com.horsetrack.equinecare.modules.inventory.dto.StockCountDTO;
import com.horsetrack.equinecare.modules.inventory.dto.StockCountRequestDTO;

import java.util.List;

public interface StockCountService {
    List<StockCountDTO> getCounts();
    StockCountDTO getCountDetail(Integer countId);
    StockCountDTO createCount(StockCountRequestDTO request);
    StockCountDTO updateCount(Integer countId, StockCountRequestDTO request);
    StockCountDTO completeCount(Integer countId);
}
