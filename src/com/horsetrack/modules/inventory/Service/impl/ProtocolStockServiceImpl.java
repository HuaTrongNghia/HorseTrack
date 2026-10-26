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
import com.horsetrack.equinecare.modules.inventory.service.DispensingHistory;
import com.horsetrack.equinecare.modules.medical.dto.TreatmentProtocolDTO;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolItem;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolPhase;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolProgressNote;
import com.horsetrack.equinecare.modules.medical.entity.TreatmentProtocol;
import com.horsetrack.equinecare.modules.medical.repository.ProtocolProgressNoteRepository;
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

    // Màn hình kho (73) chia tab theo trạng thái nên mặc định trả mọi phác đồ (trừ bản nháp): chờ kiểm tồn, đủ / thiếu hàng,
    // chờ hàng, bác sĩ đã xác nhận (chờ cấp phát), đang điều trị (đã cấp phát), tạm dừng chờ thanh toán, hoàn tất, bác sĩ đã đổi / hủy
    private static final List<String> QUEUE_STATES = List.of("WAITING_STOCK_CHECK", "STOCK_AVAILABLE", "STOCK_SHORTAGE", "WAITING_RESTOCK",
            "REQUIRES_VET_REVIEW", "VET_CONFIRMED", "READY_FOR_DISPENSING", "IN_TREATMENT", "ON_HOLD_PAYMENT", "COMPLETED", "SUPERSEDED", "CANCELLED");
    // Bác sĩ đã xác nhận phương án kho, kho được cấp thuốc (READY_FOR_DISPENSING là tên cũ của cùng trạng thái)
    private static final Set<String> DISPENSABLE_STATES = Set.of("VET_CONFIRMED", "READY_FOR_DISPENSING");
    // Được kiểm kho (lần đầu hoặc kiểm lại khi hàng về)
    private static final Set<String> CHECKABLE_STATES = Set.of("WAITING_STOCK_CHECK", "STOCK_SHORTAGE", "WAITING_RESTOCK", "REQUIRES_VET_REVIEW");
    private static final Set<String> OPEN_PO_STATUSES = Set.of("ORDERED", "IN_TRANSIT", "DELAYED", "PARTIALLY_RECEIVED");

    private final TreatmentProtocolRepository protocolRepository;
    private final StockHoldRepository holdRepository;
    private final PurchaseOrderRepository orderRepository;
    private final StockLedgerService stockLedger;
    private final TreatmentProtocolMapper mapper;
    private final ProtocolProgressNoteRepository noteRepository;
    private final com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling phaseBilling;
    private final DispensingHistory dispensingHistory;

    @Override
    @Transactional
    public List<TreatmentProtocolDTO> getQueue(String state) {
        phaseBilling.run(LocalDate.now());
        // state: một hoặc nhiều trạng thái cách nhau bằng dấu phẩy; bỏ trống = mọi trạng thái của màn hình kho
        List<String> states = StringUtils.hasText(state)
                ? Arrays.stream(state.split(",")).map(s -> s.trim().toUpperCase()).filter(StringUtils::hasText).toList()
                : QUEUE_STATES;
        return mapper.toSummaryList(protocolRepository.findByStateInOrderByRequiredByAscCreatedAtAsc(states));
    }

    @Override
    @Transactional
    public TreatmentProtocolDTO getDetail(Integer protocolId) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        phaseBilling.evaluate(protocol, LocalDate.now());
        return mapper.toDTO(protocol, true);
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
    public TreatmentProtocolDTO dispense(Integer protocolId, List<Integer> phaseNos) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        if ("IN_TREATMENT".equals(protocol.getState()) && (phaseNos == null || phaseNos.isEmpty())) {
            return dispenseSupplement(protocol);
        }
        if (com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling.ON_HOLD.equals(protocol.getState())) {
            throw new IllegalArgumentException("Phác đồ " + protocol.getProtocolCode() + " đang chờ chủ ngựa thanh toán, chưa cấp thêm thuốc.");
        }
        if (!DISPENSABLE_STATES.contains(protocol.getState()) && !"IN_TREATMENT".equals(protocol.getState())) {
            throw new IllegalArgumentException("Chỉ cấp thuốc khi bác sĩ đã xác nhận phác đồ (VET_CONFIRMED). Hiện tại: " + protocol.getState() + ".");
        }
        Set<Integer> already = phaseBilling.dispensedPhases(protocol);
        List<ProtocolPhase> selected = protocol.getPhases().stream()
                .filter(ph -> phaseNos == null || phaseNos.isEmpty() || phaseNos.contains(ph.getPhaseNo()))
                .filter(ph -> !already.contains(ph.getPhaseNo()))
                .toList();
        if (phaseNos != null) {
            for (Integer no : phaseNos) {
                if (protocol.getPhases().stream().noneMatch(ph -> ph.getPhaseNo().equals(no))) {
                    throw new IllegalArgumentException("Phác đồ " + protocol.getProtocolCode() + " không có giai đoạn " + no + ".");
                }
                if (already.contains(no)) {
                    throw new IllegalArgumentException("Giai đoạn " + no + " đã được cấp thuốc.");
                }
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("Không còn giai đoạn nào cần cấp thuốc.");
        }
        Map<Integer, Double> need = new LinkedHashMap<>();
        for (ProtocolPhase phase : selected) {
            for (ProtocolItem it : phase.getItems()) {
                need.merge(it.getItem().getItemId(), it.getQuantity() == null ? 0d : it.getQuantity(), Double::sum);
            }
        }
        List<StockHold> held = holdRepository.findByProtocolIdAndStatusIn(protocolId, List.of("HELD", "SHORT"));
        if (held.stream().anyMatch(h -> "SHORT".equals(h.getStatus()) && need.containsKey(h.getItem().getItemId()))) {
            throw new IllegalArgumentException("Phác đồ " + protocol.getProtocolCode() + " chưa được giữ đủ hàng cho giai đoạn được chọn. Hãy kiểm kho lại.");
        }
        String issueNote = "Cấp thuốc cho phác đồ " + protocol.getProtocolCode();
        for (Map.Entry<Integer, Double> entry : need.entrySet()) {
            double left = round2(entry.getValue());
            if (left <= 0) {
                continue;
            }
            List<StockHold> rows = held.stream()
                    .filter(h -> "HELD".equals(h.getStatus()) && h.getItem().getItemId().equals(entry.getKey()) && h.getQtyHeld() != null && h.getQtyHeld() > 0)
                    .sorted(Comparator.comparing(StockHold::getHoldId))
                    .toList();
            for (StockHold h : rows) {
                if (left <= 0) {
                    break;
                }
                double take = round2(Math.min(left, h.getQtyHeld()));
                var issued = stockLedger.issueHeldFefo(h.getItem().getItemId(), take, "PROTOCOL", protocolId, issueNote + " " + DispensingHistory.holdTag(h.getHoldId()));
                issued.stream().map(t -> t.getLot()).filter(Objects::nonNull).findFirst().ifPresent(h::setLot);
                h.setQtyHeld(round2(h.getQtyHeld() - take));
                h.setQtyNeeded(round2(Math.max(0, (h.getQtyNeeded() == null ? 0 : h.getQtyNeeded()) - take)));
                if (h.getQtyHeld() <= 0.001) {
                    h.setStatus("ISSUED");
                }
                holdRepository.save(h);
                left = round2(left - take);
            }
            if (left > 0.001) {
                throw new IllegalArgumentException("Kho chưa giữ đủ thuốc cho giai đoạn được chọn.");
            }
        }
        User issuer = stockLedger.currentUser();
        StringBuilder marked = new StringBuilder(protocol.getNote() == null ? "" : protocol.getNote());
        for (ProtocolPhase phase : selected) {
            String line = "Đã cấp thuốc giai đoạn " + phase.getPhaseNo();
            noteRepository.save(ProtocolProgressNote.builder()
                    .protocol(protocol)
                    .writtenBy(issuer)
                    .writtenAt(LocalDateTime.now())
                    .content(line)
                    .build());
            if (marked.indexOf(line) < 0) {
                if (!marked.isEmpty()) {
                    marked.append(" | ");
                }
                marked.append(line);
            }
        }
        String noteText = marked.toString();
        protocol.setNote(noteText.length() > 1000 ? noteText.substring(noteText.length() - 1000) : noteText);
        Integer first = protocol.getPhases().stream().map(ProtocolPhase::getPhaseNo).min(Integer::compareTo).orElse(1);
        boolean firstDispensed = already.contains(first) || selected.stream().anyMatch(ph -> ph.getPhaseNo().equals(first));
        if (firstDispensed && phaseBilling.phase1Unpaid(protocol)) {
            phaseBilling.holdUntilPhase1Paid(protocol);
        } else if (!phaseBilling.phase1Unpaid(protocol)) {
            protocol.setState("IN_TREATMENT");
            protocolRepository.save(protocol);
        } else {
            protocolRepository.save(protocol);
        }
        return mapper.toDTO(findProtocol(protocolId), true);
    }

    private static final Set<String> RECEIPT_RECHECK_STATES = Set.of("WAITING_STOCK_CHECK", "STOCK_SHORTAGE", "WAITING_RESTOCK", "REQUIRES_VET_REVIEW");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recheckAfterReceipt(Integer protocolId) {
        TreatmentProtocol protocol = protocolRepository.findById(protocolId).orElse(null);
        if (protocol == null || !RECEIPT_RECHECK_STATES.contains(protocol.getState())) {
            return;
        }
        StockCheckRequestDTO request = new StockCheckRequestDTO();
        request.setNote("Kiểm lại sau khi nhập hàng");
        TreatmentProtocolDTO checked = checkStock(protocolId, request);
        if ("STOCK_SHORTAGE".equals(checked.getState())) {
            TreatmentProtocol again = findProtocol(protocolId);
            again.setState("REQUIRES_VET_REVIEW");
            again.setIsDelayed(again.getWaitEta() != null && again.getRequiredBy() != null && again.getWaitEta().isAfter(again.getRequiredBy()));
            protocolRepository.save(again);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO reviewSupplements(Integer protocolId) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        if (!"IN_TREATMENT".equals(protocol.getState())) {
            throw new IllegalArgumentException("Chỉ duyệt thuốc bổ sung khi phác đồ đang điều trị.");
        }
        List<StockHold> pending = holdRepository.findByProtocolIdAndStatusIn(protocolId, List.of("PENDING_REVIEW"));
        if (pending.isEmpty()) {
            throw new IllegalArgumentException("Không có thuốc bổ sung đang chờ kho duyệt.");
        }
        for (StockHold h : pending) {
            double need = round2(h.getQtyNeeded() == null ? 0 : h.getQtyNeeded());
            double available = round2(Math.max(0, stockLedger.availableLocked(h.getItem().getItemId())));
            double held = Math.min(need, available);
            if (held > 0) {
                stockLedger.hold(h.getItem().getItemId(), held, "PROTOCOL", protocolId,
                        "Kho duyệt thuốc bổ sung cho phác đồ " + protocol.getProtocolCode());
            }
            h.setQtyAvailable(available);
            h.setQtyHeld(held);
            h.setStatus(held >= need && need > 0 ? "HELD" : "SHORT");
            h.setCheckedBy(stockLedger.currentUser());
            h.setCheckedAt(LocalDateTime.now());
            holdRepository.save(h);
            if ("HELD".equals(h.getStatus())) {
                protocol.getPhases().stream()
                        .flatMap(ph -> ph.getItems().stream().map(it -> new Object[] { ph, it }))
                        .filter(pair -> ((com.horsetrack.equinecare.modules.medical.entity.ProtocolItem) pair[1]).getItem().getItemId().equals(h.getItem().getItemId()))
                        .findFirst()
                        .ifPresent(pair -> phaseBilling.issueSupplementInvoice(protocol,
                                (com.horsetrack.equinecare.modules.medical.entity.ProtocolPhase) pair[0],
                                (com.horsetrack.equinecare.modules.medical.entity.ProtocolItem) pair[1],
                                java.math.BigDecimal.valueOf(need), "Kho duyệt thuốc bổ sung"));
            }
        }
        return mapper.toDTO(protocol, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DispensingHistory.Record> dispensingHistory(Integer protocolId) {
        findProtocol(protocolId);
        return dispensingHistory.records(protocolId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseHolds(Integer protocolId, String note) {
        for (StockHold h : holdRepository.findByProtocolIdAndStatusIn(protocolId, List.of("HELD", "SHORT", "PENDING_REVIEW"))) {
            if (h.getQtyHeld() != null && h.getQtyHeld() > 0) {
                stockLedger.release(h.getItem().getItemId(), h.getQtyHeld(), "PROTOCOL", protocolId, note);
            }
            h.setStatus("RELEASED");
            holdRepository.save(h);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockHold holdSupplement(TreatmentProtocol protocol, InventoryItem item, double quantity, String note) {
        double need = round2(quantity);
        double available = round2(Math.max(0, stockLedger.availableLocked(item.getItemId())));
        double held = Math.min(need, available);
        if (held > 0) {
            stockLedger.hold(item.getItemId(), held, "PROTOCOL", protocol.getProtocolId(), note);
        }
        return holdRepository.save(StockHold.builder()
                .protocolId(protocol.getProtocolId())
                .item(item)
                .qtyNeeded(need)
                .qtyAvailable(available)
                .qtyHeld(held)
                .status(held >= need ? "HELD" : "SHORT")
                .checkedBy(stockLedger.currentUser())
                .checkedAt(LocalDateTime.now())
                .build());
    }

    /**
     * Cấp thuốc bổ sung cho phác đồ đang điều trị: bù phần còn thiếu từ tồn kho hiện tại rồi cấp mọi phần đang giữ.
     * Hóa đơn thuốc bổ sung phải thanh toán trước (trừ ca cấp cứu - BR_17).
     */
    private TreatmentProtocolDTO dispenseSupplement(TreatmentProtocol protocol) {
        Integer protocolId = protocol.getProtocolId();
        List<StockHold> pending = holdRepository.findByProtocolIdAndStatusIn(protocolId, List.of("HELD", "SHORT"));
        if (pending.isEmpty()) {
            throw new IllegalArgumentException("Phác đồ " + protocol.getProtocolCode() + " đang điều trị và không có thuốc bổ sung chờ cấp.");
        }
        if (!phaseBilling.isEmergency(protocol)) {
            phaseBilling.openSupplementInvoices(protocolId).stream().findFirst().ifPresent(inv -> {
                throw new IllegalArgumentException("Chủ ngựa chưa thanh toán hóa đơn thuốc bổ sung " + inv.getInvoiceCode() + ", chưa thể cấp thuốc bổ sung.");
            });
        }
        String ref = "Giữ hàng thuốc bổ sung cho phác đồ " + protocol.getProtocolCode();
        for (StockHold h : pending) {
            if ("SHORT".equals(h.getStatus())) {
                double missing = round2(h.getQtyNeeded() - h.getQtyHeld());
                double available = round2(Math.max(0, stockLedger.availableLocked(h.getItem().getItemId())));
                double extra = Math.min(missing, available);
                if (extra > 0) {
                    stockLedger.hold(h.getItem().getItemId(), extra, "PROTOCOL", protocolId, ref);
                    h.setQtyHeld(round2(h.getQtyHeld() + extra));
                    h.setQtyAvailable(Math.max(h.getQtyAvailable(), h.getQtyHeld()));
                }
                if (h.getQtyHeld() < h.getQtyNeeded()) {
                    throw new IllegalArgumentException("Kho chưa đủ " + h.getItem().getItemName() + " cho thuốc bổ sung (thiếu "
                            + StockLedgerService.fmt(h.getQtyNeeded() - h.getQtyHeld()) + " " + h.getItem().getUnit() + ").");
                }
                h.setStatus("HELD");
            }
        }
        String note = "Cấp thuốc bổ sung cho phác đồ " + protocol.getProtocolCode();
        for (StockHold h : pending) {
            var issued = stockLedger.issueHeldFefo(h.getItem().getItemId(), h.getQtyHeld(), "PROTOCOL", protocolId, note + " " + DispensingHistory.holdTag(h.getHoldId()));
            issued.stream().map(t -> t.getLot()).filter(java.util.Objects::nonNull).findFirst().ifPresent(h::setLot);
            h.setStatus("ISSUED");
            h.setCheckedBy(stockLedger.currentUser());
            h.setCheckedAt(LocalDateTime.now());
            holdRepository.save(h);
        }
        return mapper.toDTO(protocol, true);
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
