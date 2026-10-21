package com.horsetrack.equinecare.modules.inventory.service;

import com.horsetrack.equinecare.modules.inventory.dto.StockCheckRequestDTO;
import com.horsetrack.equinecare.modules.medical.dto.TreatmentProtocolDTO;

import java.util.List;

/** Phần việc của kho với phác đồ điều trị (SRS màn hình 73–74, BR_07). */
public interface ProtocolStockService {
    List<TreatmentProtocolDTO> getQueue(String state);
    TreatmentProtocolDTO getDetail(Integer protocolId);
    TreatmentProtocolDTO checkStock(Integer protocolId, StockCheckRequestDTO request);
    TreatmentProtocolDTO dispense(Integer protocolId);

    /** Trả lại toàn bộ hàng đang giữ của phác đồ (dùng khi hủy hoặc tạo phiên bản mới). */
    void releaseHolds(Integer protocolId, String note);
}
