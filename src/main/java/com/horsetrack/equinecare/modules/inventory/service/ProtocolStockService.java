package com.horsetrack.equinecare.modules.inventory.service;

import com.horsetrack.equinecare.modules.inventory.dto.StockCheckRequestDTO;
import com.horsetrack.equinecare.modules.medical.dto.TreatmentProtocolDTO;

import java.util.List;

/** Phần việc của kho với phác đồ điều trị (SRS màn hình 73–74, BR_07). */
import com.horsetrack.equinecare.modules.medical.entity.TreatmentProtocol;

public interface ProtocolStockService {
    List<TreatmentProtocolDTO> getQueue(String state);
    TreatmentProtocolDTO getDetail(Integer protocolId);
    TreatmentProtocolDTO checkStock(Integer protocolId, StockCheckRequestDTO request);
    TreatmentProtocolDTO dispense(Integer protocolId);

    /** Trả lại toàn bộ hàng đang giữ của phác đồ (dùng khi hủy hoặc tạo phiên bản mới). */
    void releaseHolds(Integer protocolId, String note);

    /**
     * Thuốc bổ sung khi phác đồ đang điều trị: giữ ngay phần đang có trong kho (HELD) hoặc ghi thiếu (SHORT);
     * kho cấp phần bổ sung bằng dispense khi phác đồ IN_TREATMENT.
     */
    com.horsetrack.equinecare.modules.inventory.entity.StockHold holdSupplement(TreatmentProtocol protocol,
            com.horsetrack.equinecare.modules.inventory.entity.InventoryItem item, double quantity, String note);
}
