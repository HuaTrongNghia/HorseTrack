package com.horsetrack.equinecare.modules.inventory.service;

import com.horsetrack.equinecare.modules.inventory.dto.*;

import java.util.List;

public interface PurchaseOrderService {
    List<PurchaseOrderDTO> getOrders(String status, Integer supplierId);
    PurchaseOrderDTO getOrderDetail(Integer orderId);
    PurchaseOrderDTO createOrder(PurchaseOrderCreateRequestDTO request);
    PurchaseOrderDTO markInTransit(Integer orderId);
    PurchaseOrderDTO updateEta(Integer orderId, PurchaseOrderEtaRequestDTO request);
    PurchaseOrderDTO cancelOrder(Integer orderId, ReasonRequestDTO request);
    PurchaseOrderDTO receiveOrder(Integer orderId, PurchaseOrderReceiveRequestDTO request);
}
