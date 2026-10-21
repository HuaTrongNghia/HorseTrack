package com.horsetrack.equinecare.modules.inventory.service.impl;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.inventory.dto.*;
import com.horsetrack.equinecare.modules.inventory.entity.*;
import com.horsetrack.equinecare.modules.inventory.repository.*;
import com.horsetrack.equinecare.modules.inventory.service.PurchaseOrderService;
import com.horsetrack.equinecare.modules.inventory.service.StockLedgerService;
import com.horsetrack.equinecare.modules.medical.entity.TreatmentProtocol;
import com.horsetrack.equinecare.modules.medical.repository.TreatmentProtocolRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.horsetrack.equinecare.modules.inventory.service.StockLedgerService.fmt;
import static com.horsetrack.equinecare.modules.inventory.service.StockLedgerService.round2;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    // Đơn còn đang chờ hàng (chưa nhận đủ, chưa hủy)
    private static final Set<String> OPEN_STATUSES = Set.of("ORDERED", "IN_TRANSIT", "DELAYED", "PARTIALLY_RECEIVED");
    // Yêu cầu vật tư còn có thể gắn với đơn mua
    private static final Set<String> REQUISITION_OPEN_STATUSES = Set.of("SUBMITTED", "CHECKING_STOCK", "WAITING_RESTOCK");

    private final PurchaseOrderRepository orderRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryItemRepository itemRepository;
    private final MaterialRequisitionRepository requisitionRepository;
    private final StockLedgerService stockLedger;
    private final TreatmentProtocolRepository protocolRepository;
    private final StockHoldRepository holdRepository;
    private final EntityManager entityManager;

    // Phác đồ còn đang chờ thuốc, có thể đặt mua cho phác đồ đó
    private static final Set<String> PROTOCOL_WAITING_STATES = Set.of("WAITING_STOCK_CHECK", "STOCK_SHORTAGE", "WAITING_RESTOCK", "REQUIRES_VET_REVIEW");

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderDTO> getOrders(String status, Integer supplierId) {
        String st = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        return orderRepository.search(st, supplierId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderDTO getOrderDetail(Integer orderId) {
        return toDTO(findOrder(orderId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderDTO createOrder(PurchaseOrderCreateRequestDTO request) {
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + request.getSupplierId()));
        if (!Boolean.TRUE.equals(supplier.getIsActive())) {
            throw new IllegalArgumentException("Nhà cung cấp '" + supplier.getSupplierName() + "' đã ngừng hợp tác.");
        }
        if (request.getRequiredBy() != null && request.getRequiredBy().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Ngày cần hàng (requiredBy) không được ở trong quá khứ.");
        }

        String purpose = request.getPurpose().toUpperCase();
        MaterialRequisition requisition = null;
        if ("REQUISITION".equals(purpose)) {
            if (request.getRequisitionId() == null) {
                throw new IllegalArgumentException("Đơn mua cho yêu cầu vật tư phải có requisitionId.");
            }
            requisition = requisitionRepository.findById(request.getRequisitionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu vật tư với ID: " + request.getRequisitionId()));
            if (!REQUISITION_OPEN_STATUSES.contains(requisition.getStatus())) {
                throw new IllegalArgumentException("Yêu cầu vật tư " + requisition.getRequisitionCode()
                        + " đang ở trạng thái " + requisition.getStatus() + ", không thể đặt mua cho yêu cầu này.");
            }
            Integer reqItemId = requisition.getItem().getItemId();
            boolean containsItem = request.getLines().stream().anyMatch(l -> reqItemId.equals(l.getItemId()));
            if (!containsItem) {
                throw new IllegalArgumentException("Đơn mua phải có vật tư của yêu cầu " + requisition.getRequisitionCode()
                        + " (" + requisition.getItem().getItemName() + ").");
            }
        } else if (request.getRequisitionId() != null) {
            throw new IllegalArgumentException("Chỉ gửi requisitionId khi purpose = REQUISITION.");
        }

        TreatmentProtocol protocol = null;
        if ("PROTOCOL".equals(purpose)) {
            if (request.getProtocolId() == null) {
                throw new IllegalArgumentException("Đơn mua cho phác đồ phải có protocolId.");
            }
            protocol = protocolRepository.findById(request.getProtocolId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phác đồ với ID: " + request.getProtocolId()));
            if (!PROTOCOL_WAITING_STATES.contains(protocol.getState())) {
                throw new IllegalArgumentException("Phác đồ " + protocol.getProtocolCode() + " đang ở trạng thái "
                        + protocol.getState() + ", không cần đặt mua thuốc.");
            }
        } else if (request.getProtocolId() != null) {
            throw new IllegalArgumentException("Chỉ gửi protocolId khi purpose = PROTOCOL.");
        }

        PurchaseOrder order = PurchaseOrder.builder()
                .supplier(supplier)
                .purpose(purpose)
                .status("ORDERED")
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                // Đơn cho phác đồ: mặc định ngày cần hàng là ngày bác sĩ cần thuốc
                .requiredBy(request.getRequiredBy() != null ? request.getRequiredBy() : (protocol != null ? protocol.getRequiredBy() : null))
                .orderedAt(LocalDateTime.now())
                .createdBy(stockLedger.currentUser())
                .note(trimToNull(request.getNote()))
                .requisitionId(requisition != null ? requisition.getRequisitionId() : null)
                .protocolId(protocol != null ? protocol.getProtocolId() : null)
                .build();
        if (isLate(order.getExpectedDeliveryDate(), order.getRequiredBy())) {
            order.setStatus("DELAYED");
        }

        Set<Integer> seenItems = new HashSet<>();
        for (PurchaseOrderCreateRequestDTO.Line lineDto : request.getLines()) {
            if (!seenItems.add(lineDto.getItemId())) {
                throw new IllegalArgumentException("Vật tư ID " + lineDto.getItemId() + " bị lặp trong đơn. Hãy gộp thành một dòng.");
            }
            InventoryItem item = itemRepository.findById(lineDto.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vật tư với ID: " + lineDto.getItemId()));
            order.addLine(PurchaseOrderLine.builder()
                    .item(item)
                    .quantityOrdered(round2(lineDto.getQuantity()))
                    .unitPrice(lineDto.getUnitPrice() != null ? lineDto.getUnitPrice() : item.getUnitPrice())
                    .build());
        }

        PurchaseOrder saved = orderRepository.saveAndFlush(order);
        // Đọc lại để lấy order_code do DB tự sinh
        entityManager.refresh(saved);

        if (requisition != null && !"WAITING_RESTOCK".equals(requisition.getStatus())) {
            requisition.setStatus("WAITING_RESTOCK");
            requisitionRepository.save(requisition);
        }
        if (protocol != null) {
            // Gắn đơn vào các dòng thiếu hàng của phác đồ, rồi báo bác sĩ nếu hàng về trễ
            Set<Integer> orderItems = saved.getLines().stream().map(l -> l.getItem().getItemId()).collect(Collectors.toSet());
            for (StockHold hold : holdRepository.findByProtocolIdAndStatusIn(protocol.getProtocolId(), List.of("SHORT"))) {
                if (orderItems.contains(hold.getItem().getItemId())) {
                    hold.setPurchaseOrderId(saved.getOrderId());
                    hold.setEta(saved.getExpectedDeliveryDate());
                    holdRepository.save(hold);
                }
            }
            flagProtocolIfLate(saved);
        }
        return toDTO(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderDTO markInTransit(Integer orderId) {
        PurchaseOrder order = findOrder(orderId);
        if (!"ORDERED".equals(order.getStatus()) && !"DELAYED".equals(order.getStatus())) {
            throw new IllegalArgumentException("Chỉ đơn ORDERED hoặc DELAYED mới chuyển sang IN_TRANSIT (hiện tại: " + order.getStatus() + ").");
        }
        order.setStatus("IN_TRANSIT");
        return toDTO(orderRepository.save(order));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderDTO updateEta(Integer orderId, PurchaseOrderEtaRequestDTO request) {
        PurchaseOrder order = findOrder(orderId);
        if (!OPEN_STATUSES.contains(order.getStatus())) {
            throw new IllegalArgumentException("Không thể đổi ngày về của đơn đang ở trạng thái " + order.getStatus() + ".");
        }
        LocalDate oldEta = order.getExpectedDeliveryDate();
        if (request.getExpectedDeliveryDate().equals(oldEta)) {
            throw new IllegalArgumentException("Ngày dự kiến mới trùng với ngày hiện tại (" + oldEta + ").");
        }
        order.setPreviousEta(oldEta);
        order.setExpectedDeliveryDate(request.getExpectedDeliveryDate());
        order.setNote(appendNote(order.getNote(), "Đổi ETA " + oldEta + " -> " + request.getExpectedDeliveryDate()
                + (StringUtils.hasText(request.getNote()) ? ": " + request.getNote().trim() : "")));

        // ETA trễ hơn ngày cần hàng thì đánh dấu DELAYED (BR_07)
        if (isLate(order.getExpectedDeliveryDate(), order.getRequiredBy()) && !"PARTIALLY_RECEIVED".equals(order.getStatus())) {
            order.setStatus("DELAYED");
        }
        orderRepository.save(order);

        // Cập nhật ngày về trên các dòng thiếu hàng của phác đồ đang chờ đơn này
        for (StockHold hold : holdRepository.findByPurchaseOrderIdAndStatus(order.getOrderId(), "SHORT")) {
            hold.setEta(order.getExpectedDeliveryDate());
            holdRepository.save(hold);
        }
        flagProtocolIfLate(order);
        return toDTO(order);
    }

    /**
     * BR_07 (Delayed Shipment Alert): đơn mua cho phác đồ về trễ hơn ngày bác sĩ cần thuốc
     * thì phác đồ chuyển sang REQUIRES_VET_REVIEW để bác sĩ quyết định chờ tiếp hay đổi phác đồ.
     */
    private void flagProtocolIfLate(PurchaseOrder order) {
        if (order.getProtocolId() == null) {
            return;
        }
        protocolRepository.findById(order.getProtocolId()).ifPresent(protocol -> {
            boolean late = isLate(order.getExpectedDeliveryDate(), protocol.getRequiredBy());
            if (late && Set.of("STOCK_SHORTAGE", "WAITING_RESTOCK").contains(protocol.getState())) {
                protocol.setState("REQUIRES_VET_REVIEW");
                protocol.setIsDelayed(true);
                protocol.setWaitEta(order.getExpectedDeliveryDate());
                protocolRepository.save(protocol);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderDTO cancelOrder(Integer orderId, ReasonRequestDTO request) {
        PurchaseOrder order = findOrder(orderId);
        if (!OPEN_STATUSES.contains(order.getStatus())) {
            throw new IllegalArgumentException("Không thể hủy đơn đang ở trạng thái " + order.getStatus() + ".");
        }
        boolean anyReceived = order.getLines().stream().anyMatch(l -> l.getQuantityReceived() != null && l.getQuantityReceived() > 0);
        if (anyReceived) {
            throw new IllegalArgumentException("Đơn đã nhận một phần hàng, không thể hủy. Hãy liên hệ nhà cung cấp để xử lý phần còn lại.");
        }
        order.setStatus("CANCELLED");
        order.setNote(appendNote(order.getNote(), "Hủy đơn: " + request.getReason().trim()));
        orderRepository.save(order);

        // Yêu cầu vật tư đang chờ đơn này thì trả về SUBMITTED để kho xử lý lại
        if (order.getRequisitionId() != null) {
            requisitionRepository.findById(order.getRequisitionId()).ifPresent(req -> {
                boolean otherOpenOrder = orderRepository.findByRequisitionId(req.getRequisitionId()).stream()
                        .anyMatch(o -> !o.getOrderId().equals(order.getOrderId()) && OPEN_STATUSES.contains(o.getStatus()));
                if ("WAITING_RESTOCK".equals(req.getStatus()) && !otherOpenOrder) {
                    req.setStatus("SUBMITTED");
                    requisitionRepository.save(req);
                }
            });
        }
        return toDTO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderDTO receiveOrder(Integer orderId, PurchaseOrderReceiveRequestDTO request) {
        PurchaseOrder order = findOrder(orderId);
        if (!OPEN_STATUSES.contains(order.getStatus())) {
            throw new IllegalArgumentException("Không thể nhận hàng cho đơn đang ở trạng thái " + order.getStatus() + ".");
        }

        Map<Integer, PurchaseOrderLine> linesById = new HashMap<>();
        order.getLines().forEach(l -> linesById.put(l.getPoLineId(), l));
        String note = trimToNull(request.getNote());

        Set<Integer> seenLines = new HashSet<>();
        for (PurchaseOrderReceiveRequestDTO.Line r : request.getLines()) {
            if (!seenLines.add(r.getPoLineId())) {
                throw new IllegalArgumentException("Dòng đơn hàng " + r.getPoLineId() + " bị lặp trong yêu cầu nhận hàng.");
            }
            PurchaseOrderLine line = linesById.get(r.getPoLineId());
            if (line == null) {
                throw new IllegalArgumentException("Dòng " + r.getPoLineId() + " không thuộc đơn " + order.getOrderCode() + ".");
            }
            double qty = round2(r.getQuantityReceived());
            double remaining = round2(line.getRemainingQuantity());
            if (qty > remaining) {
                throw new IllegalArgumentException(String.format("Vật tư %s chỉ còn %s chưa nhận, không thể nhận %s.",
                        line.getItem().getItemName(), fmt(remaining), fmt(qty)));
            }

            // Nhập kho qua sổ cái, gắn lô với dòng đơn hàng
            // Số lô tùy chọn: để trống thì sổ cái tự sinh
            var tx = stockLedger.receive(line.getItem().getItemId(), qty, r.getLotNo(), r.getExpiryDate(),
                    line.getPoLineId(), "PURCHASE_ORDER", order.getOrderId(),
                    note != null ? note : "Nhận hàng đơn " + order.getOrderCode());

            line.setQuantityReceived(round2((line.getQuantityReceived() != null ? line.getQuantityReceived() : 0) + qty));
            line.setLotNo(tx.getLot() != null ? tx.getLot().getLotNo() : r.getLotNo());
            line.setExpiryDate(r.getExpiryDate());
        }

        boolean fullyReceived = order.getLines().stream().allMatch(l -> l.getRemainingQuantity() <= 0);
        if (fullyReceived) {
            order.setStatus("RECEIVED");
            order.setReceivedAt(LocalDateTime.now());
        } else {
            order.setStatus("PARTIALLY_RECEIVED");
        }
        orderRepository.save(order);

        // Hàng của yêu cầu vật tư đã về đủ: chuyển yêu cầu sang CHECKING_STOCK để kho cấp cho Groom
        if (fullyReceived && order.getRequisitionId() != null) {
            requisitionRepository.findById(order.getRequisitionId()).ifPresent(req -> {
                if ("WAITING_RESTOCK".equals(req.getStatus())) {
                    req.setStatus("CHECKING_STOCK");
                    requisitionRepository.save(req);
                }
            });
        }
        return toDTO(order);
    }

    // ==========================================
    // HÀM HỖ TRỢ
    // ==========================================

    private PurchaseOrder findOrder(Integer orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn mua hàng với ID: " + orderId));
    }

    private static boolean isLate(LocalDate eta, LocalDate requiredBy) {
        return eta != null && requiredBy != null && eta.isAfter(requiredBy);
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    // Ghi chú cột note tối đa 500 ký tự; giữ phần mới nhất
    private static String appendNote(String current, String addition) {
        String merged = StringUtils.hasText(current) ? current + " | " + addition : addition;
        return merged.length() > 500 ? merged.substring(merged.length() - 500) : merged;
    }

    private PurchaseOrderDTO toDTO(PurchaseOrder o) {
        List<PurchaseOrderDTO.Line> lines = o.getLines().stream().map(l -> {
            BigDecimal price = l.getUnitPrice() != null ? l.getUnitPrice() : BigDecimal.ZERO;
            return PurchaseOrderDTO.Line.builder()
                    .poLineId(l.getPoLineId())
                    .itemId(l.getItem().getItemId())
                    .itemCode(l.getItem().getItemCode())
                    .itemName(l.getItem().getItemName())
                    .unit(l.getItem().getUnit())
                    .quantityOrdered(l.getQuantityOrdered())
                    .quantityReceived(l.getQuantityReceived() != null ? l.getQuantityReceived() : 0.0)
                    .quantityRemaining(round2(l.getRemainingQuantity()))
                    .unitPrice(l.getUnitPrice())
                    .lineTotal(price.multiply(BigDecimal.valueOf(l.getQuantityOrdered())).setScale(2, java.math.RoundingMode.HALF_UP))
                    .lotNo(l.getLotNo())
                    .expiryDate(l.getExpiryDate())
                    .build();
        }).collect(Collectors.toList());

        return PurchaseOrderDTO.builder()
                .orderId(o.getOrderId())
                .orderCode(o.getOrderCode())
                .supplierId(o.getSupplier() != null ? o.getSupplier().getSupplierId() : null)
                .supplierName(o.getSupplier() != null ? o.getSupplier().getSupplierName() : null)
                .purpose(o.getPurpose())
                .status(o.getStatus())
                .expectedDeliveryDate(o.getExpectedDeliveryDate())
                .previousEta(o.getPreviousEta())
                .requiredBy(o.getRequiredBy())
                .etaLaterThanRequired(isLate(o.getExpectedDeliveryDate(), o.getRequiredBy()))
                .orderedAt(o.getOrderedAt())
                .receivedAt(o.getReceivedAt())
                .createdByName(o.getCreatedBy() != null ? o.getCreatedBy().getFullName() : null)
                .note(o.getNote())
                .protocolId(o.getProtocolId())
                .requisitionId(o.getRequisitionId())
                .totalAmount(lines.stream().map(PurchaseOrderDTO.Line::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add))
                .lines(lines)
                .build();
    }
}
