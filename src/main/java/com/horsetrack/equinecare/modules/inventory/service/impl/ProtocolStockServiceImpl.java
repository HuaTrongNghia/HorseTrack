package com.horsetrack.equinecare.modules.inventory.service.impl;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.inventory.dto.StockCheckRequestDTO;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.entity.PurchaseOrder;
import com.horsetrack.equinecare.modules.inventory.entity.StockHold;
import com.horsetrack.equinecare.modules.inventory.repository.PurchaseOrderRepository;
import com.horsetrack.equinecare.modules.inventory.repository.StockHoldRepository;
import com.horsetrack.equinecare.modules.inventory.service.ProtocolStockService;
import com.horsetrack.equinecare.modules.inventory.service.StockLedgerService;
import com.horsetrack.equinecare.modules.medical.dto.TreatmentProtocolDTO;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolItem;
import com.horsetrack.equinecare.modules.medical.entity.TreatmentProtocol;
import com.horsetrack.equinecare.modules.medical.repository.TreatmentProtocolRepository;
import com.horsetrack.equinecare.modules.medical.service.TreatmentProtocolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.horsetrack.equinecare.modules.inventory.service.StockLedgerService.round2;

@Service
@RequiredArgsConstructor
public class ProtocolStockServiceImpl implements ProtocolStockService {

    // Phác đồ đang chờ kho xử lý (hàng đợi mặc định của màn hình 73)
    private static final List<String> QUEUE_STATES = List.of("WAITING_STOCK_CHECK", "WAITING_RESTOCK", "REQUIRES_VET_REVIEW", "VET_CONFIRMED");
    // Được kiểm kho (lần đầu hoặc kiểm lại khi hàng về)
    private static final Set<String> CHECKABLE_STATES = Set.of("WAITING_STOCK_CHECK", "STOCK_SHORTAGE", "WAITING_RESTOCK", "REQUIRES_VET_REVIEW");
    private static final Set<String> OPEN_PO_STATUSES = Set.of("ORDERED", "IN_TRANSIT", "DELAYED", "PARTIALLY_RECEIVED");

    private final TreatmentProtocolRepository protocolRepository;
    private final StockHoldRepository holdRepository;
    private final PurchaseOrderRepository orderRepository;
    private final StockLedgerService stockLedger;
    private final TreatmentProtocolMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<TreatmentProtocolDTO> getQueue(String state) {
        List<String> states = StringUtils.hasText(state) ? List.of(state.trim().toUpperCase()) : QUEUE_STATES;
        return mapper.toSummaryList(protocolRepository.findByStateInOrderByRequiredByAscCreatedAtAsc(states));
    }

    @Override
    @Transactional(readOnly = true)
    public TreatmentProtocolDTO getDetail(Integer protocolId) {
        return mapper.toDTO(findProtocol(protocolId), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO checkStock(Integer protocolId, StockCheckRequestDTO request) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        if (!CHECKABLE_STATES.contains(protocol.getState())) {
            throw new IllegalArgumentException("Không thể kiểm kho cho phác đồ " + protocol.getProtocolCode()
                    + " đang ở trạng thái " + protocol.getState() + ".");
        }

        // Kiểm lại: trả hết phần đã giữ lần trước rồi tính lại từ đầu
        releaseHolds(protocolId, "Kiểm kho lại phác đồ " + protocol.getProtocolCode());

        Map<Integer, StockCheckRequestDTO.Shortage> shortageInfo = new HashMap<>();
        if (request != null && request.getShortages() != null) {
            for (StockCheckRequestDTO.Shortage s : request.getShortages()) {
                shortageInfo.put(s.getItemId(), s);
            }
        }

        // Cộng dồn số lượng cần theo vật tư qua mọi giai đoạn
        Map<Integer, Double> needed = new LinkedHashMap<>();
        Map<Integer, InventoryItem> items = new HashMap<>();
        protocol.getPhases().forEach(ph -> ph.getItems().forEach((ProtocolItem it) -> {
            needed.merge(it.getItem().getItemId(), it.getQuantity(), Double::sum);
            items.put(it.getItem().getItemId(), it.getItem());
        }));
        for (Integer itemId : shortageInfo.keySet()) {
            if (!needed.containsKey(itemId)) {
                throw new IllegalArgumentException("Vật tư ID " + itemId + " không có trong phác đồ " + protocol.getProtocolCode() + ".");
            }
        }

        User checker = stockLedger.currentUser();
        String ref = "Giữ hàng cho phác đồ " + protocol.getProtocolCode();
        boolean allHeld = true;
        LocalDate latestEta = null;

        for (Map.Entry<Integer, Double> e : needed.entrySet()) {
            Integer itemId = e.getKey();
            double need = round2(e.getValue());
            double available = round2(Math.max(0, stockLedger.availableLocked(itemId)));
            double held = Math.min(need, available);

            StockHold hold = StockHold.builder()
                    .protocolId(protocolId)
                    .item(items.get(itemId))
                    .qtyNeeded(need)
                    .qtyAvailable(available)
                    .qtyHeld(held)
                    .status(held >= need ? "HELD" : "SHORT")
                    .checkedBy(checker)
                    .checkedAt(LocalDateTime.now())
                    .build();

            if (held < need) {
                allHeld = false;
                StockCheckRequestDTO.Shortage info = shortageInfo.get(itemId);
                if (info != null) {
                    LocalDate eta = info.getEta();
                    if (info.getPurchaseOrderId() != null) {
                        PurchaseOrder po = validateOrderForItem(info.getPurchaseOrderId(), itemId);
                        hold.setPurchaseOrderId(po.getOrderId());
                        if (eta == null) {
                            eta = po.getExpectedDeliveryDate();
                        }
                    }
                    if (eta != null && eta.isBefore(LocalDate.now())) {
                        throw new IllegalArgumentException("Ngày dự kiến có hàng của vật tư ID " + itemId + " không được ở trong quá khứ.");
                    }
                    hold.setEta(eta);
                    if (eta != null && (latestEta == null || eta.isAfter(latestEta))) {
                        latestEta = eta;
                    }
                }
            }

            // Giữ phần đang có (kể cả khi thiếu) để không bị xuất cho việc khác
            if (held > 0) {
                stockLedger.hold(itemId, held, "PROTOCOL", protocolId, ref);
            }
            holdRepository.save(hold);
        }

        if (allHeld) {
            protocol.setState("STOCK_AVAILABLE");
            protocol.setIsDelayed(false);
            protocol.setWaitEta(null);
        } else {
            // BR_07: không cấp thuốc, trả về cho bác sĩ quyết định (chờ hàng hoặc đổi phác đồ)
            protocol.setState("STOCK_SHORTAGE");
            protocol.setWaitEta(latestEta);
            protocol.setIsDelayed(latestEta != null && protocol.getRequiredBy() != null && latestEta.isAfter(protocol.getRequiredBy()));
        }
        if (request != null && StringUtils.hasText(request.getNote())) {
            protocol.setNote(appendNote(protocol.getNote(), "Kho: " + request.getNote().trim()));
        }
        protocolRepository.save(protocol);
        return mapper.toDTO(protocol, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO dispense(Integer protocolId) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        // BR_07: chỉ cấp thuốc sau khi bác sĩ xác nhận phương án kho
        if (!"VET_CONFIRMED".equals(protocol.getState())) {
            throw new IllegalArgumentException("Chỉ cấp thuốc khi bác sĩ đã xác nhận phác đồ (VET_CONFIRMED). Hiện tại: " + protocol.getState() + ".");
        }
        List<StockHold> holds = holdRepository.findByProtocolIdAndStatusIn(protocolId, List.of("HELD", "SHORT"));
        if (holds.isEmpty() || holds.stream().anyMatch(h -> "SHORT".equals(h.getStatus()))) {
            throw new IllegalArgumentException("Phác đồ " + protocol.getProtocolCode() + " chưa được giữ đủ hàng. Hãy kiểm kho lại.");
        }

        String note = "Cấp thuốc cho phác đồ " + protocol.getProtocolCode();
        for (StockHold h : holds) {
            stockLedger.issueHeld(h.getItem().getItemId(), h.getQtyHeld(), "PROTOCOL", protocolId, note);
            h.setStatus("ISSUED");
            holdRepository.save(h);
        }
        protocol.setState("IN_TREATMENT");
        protocolRepository.save(protocol);
        return mapper.toDTO(protocol, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseHolds(Integer protocolId, String note) {
        for (StockHold h : holdRepository.findByProtocolIdAndStatusIn(protocolId, List.of("HELD", "SHORT"))) {
            if (h.getQtyHeld() != null && h.getQtyHeld() > 0) {
                stockLedger.release(h.getItem().getItemId(), h.getQtyHeld(), "PROTOCOL", protocolId, note);
            }
            h.setStatus("RELEASED");
            holdRepository.save(h);
        }
    }

    // ==========================================
    // HÀM HỖ TRỢ
    // ==========================================

    private TreatmentProtocol findProtocol(Integer protocolId) {
        return protocolRepository.findById(protocolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phác đồ với ID: " + protocolId));
    }

    private PurchaseOrder validateOrderForItem(Integer orderId, Integer itemId) {
        PurchaseOrder po = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn mua hàng với ID: " + orderId));
        if (!OPEN_PO_STATUSES.contains(po.getStatus())) {
            throw new IllegalArgumentException("Đơn mua " + po.getOrderCode() + " đang ở trạng thái " + po.getStatus() + ", không còn chờ hàng.");
        }
        boolean hasItem = po.getLines().stream().anyMatch(l -> l.getItem().getItemId().equals(itemId) && l.getRemainingQuantity() > 0);
        if (!hasItem) {
            throw new IllegalArgumentException("Đơn mua " + po.getOrderCode() + " không có (hoặc đã nhận đủ) vật tư ID " + itemId + ".");
        }
        return po;
    }

    private static String appendNote(String current, String addition) {
        String merged = StringUtils.hasText(current) ? current + " | " + addition : addition;
        return merged.length() > 1000 ? merged.substring(merged.length() - 1000) : merged;
    }
}
