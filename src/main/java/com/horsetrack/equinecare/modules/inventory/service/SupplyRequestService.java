package com.horsetrack.equinecare.modules.inventory.service;

import com.horsetrack.equinecare.modules.inventory.dto.*;

import java.util.List;

public interface SupplyRequestService {
    // Groom
    SupplyRequestDTO createRequest(SupplyRequestCreateDTO request);
    List<SupplyRequestDTO> getMyRequests();
    /** Vật tư Groom được phép yêu cầu (mọi nhóm trừ thuốc và vaccine), kèm khả dụng hiện tại. */
    List<InventoryItemDTO> getRequestableItems();
    SupplyRequestDTO cancelMyRequest(Integer requisitionId);
    SupplyRequestDTO confirmReceived(Integer requisitionId);

    // Nhân viên kho
    List<SupplyRequestDTO> getRequests(String status, String barnZone);
    SupplyRequestDTO issue(Integer requisitionId, SupplyRequestIssueDTO request);
    SupplyRequestDTO markWaitingRestock(Integer requisitionId);
    SupplyRequestDTO reject(Integer requisitionId, ReasonRequestDTO request);
}
