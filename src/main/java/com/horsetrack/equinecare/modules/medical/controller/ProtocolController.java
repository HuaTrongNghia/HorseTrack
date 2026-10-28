package com.horsetrack.equinecare.modules.medical.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.inventory.entity.PurchaseOrder;
import com.horsetrack.equinecare.modules.inventory.entity.PurchaseOrderLine;
import com.horsetrack.equinecare.modules.inventory.repository.PurchaseOrderRepository;
import com.horsetrack.equinecare.modules.medical.dto.ProtocolDTO;
import com.horsetrack.equinecare.modules.medical.dto.ShipmentLotDTO;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolItem;
import com.horsetrack.equinecare.modules.medical.entity.TreatmentProtocol;
import com.horsetrack.equinecare.modules.medical.repository.TreatmentProtocolRepository;
import com.horsetrack.equinecare.modules.medical.repository.VetCaseRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Danh sách phác đồ / lô hàng dạng tóm tắt (đọc từ DB) cho các vai trò không dùng API quản lý chuyên sâu.
 * Chủ ngựa chỉ thấy ngựa của mình, HLV chỉ thấy ngựa được phân công.
 */
@RestController
@RequestMapping({"/api/v1/protocols", "/api/protocols"})
@RequiredArgsConstructor
public class ProtocolController {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");
    private static final Set<String> CLOSED = Set.of("COMPLETED", "SUPERSEDED", "CANCELLED");
    private static final Set<String> OPEN_PO = Set.of("ORDERED", "IN_TRANSIT", "DELAYED", "PARTIALLY_RECEIVED");
    private static final Map<String, String> STATE_LABEL = Map.ofEntries(
            Map.entry("DRAFT", "Bản nháp"),
            Map.entry("WAITING_STOCK_CHECK", "Chờ kho kiểm tồn"),
            Map.entry("STOCK_SHORTAGE", "Thiếu vật tư — chờ BS quyết định"),
            Map.entry("STOCK_AVAILABLE", "Kho đủ hàng — chờ BS xác nhận"),
            Map.entry("WAITING_RESTOCK", "Chờ hàng về"),
            Map.entry("REQUIRES_VET_REVIEW", "Cần BS đánh giá lại"),
            Map.entry("VET_CONFIRMED", "BS đã xác nhận"),
            Map.entry("READY_FOR_DISPENSING", "Chờ kho cấp phát"),
            Map.entry("IN_TREATMENT", "Đang điều trị"),
            Map.entry("ON_HOLD_PAYMENT", "Tạm dừng · chờ chủ ngựa thanh toán"),
            Map.entry("COMPLETED", "Hoàn tất"),
            Map.entry("SUPERSEDED", "Đã bị thay thế"),
            Map.entry("CANCELLED", "Đã hủy"));
    private static final Map<String, String> PO_LABEL = Map.of(
            "ORDERED", "Đã đặt", "IN_TRANSIT", "Đang giao", "DELAYED", "Trễ", "PARTIALLY_RECEIVED", "Nhận một phần");

    private final TreatmentProtocolRepository protocolRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final VetCaseRepository vetCaseRepository;
    private final UserRepository userRepository;
    private final com.horsetrack.equinecare.modules.auth.repository.GroomProfileRepository groomProfileRepository;

    // Phác đồ đang mở (tab chứa "đóng" thì trả phác đồ đã đóng), lọc theo quyền xem ngựa
    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Danh sách phác đồ tóm tắt", description = "Tab có chữ \"đóng\" trả phác đồ đã đóng, còn lại trả phác đồ đang mở. Chủ ngựa, HLV và Groom chỉ thấy ngựa trong phạm vi của mình.")
    public ResponseEntity<ApiResponse<List<ProtocolDTO>>> getProtocols(@RequestParam(defaultValue = "Cần bác sĩ xử lý") String tab) {
        User user = currentUser();
        boolean closedTab = tab.toLowerCase().contains("đóng");
        List<ProtocolDTO> list = protocolRepository.search(null, null).stream()
                .filter(p -> closedTab == CLOSED.contains(p.getState()))
                .filter(p -> canView(user, p.getHorse()))
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok("Thành công", list));
    }

    // Đơn nhập hàng đang mở phục vụ phác đồ (đơn quá ETA được tính là trễ)
    @GetMapping("/shipments")
    @Transactional(readOnly = true)
    @Operation(summary = "Lô hàng phục vụ phác đồ", description = "Đơn nhập đang mở gắn với phác đồ. Đơn quá ngày dự kiến được tính là trễ.")
    public ResponseEntity<ApiResponse<List<ShipmentLotDTO>>> getRelatedShipments() {
        User user = currentUser();
        Map<Integer, TreatmentProtocol> protocols = protocolRepository.findAll().stream()
                .collect(Collectors.toMap(TreatmentProtocol::getProtocolId, p -> p, (a, b) -> a));
        Map<Integer, String> protocolCodes = protocols.values().stream()
                .collect(Collectors.toMap(TreatmentProtocol::getProtocolId, p -> Objects.toString(p.getProtocolCode(), "PD-" + p.getProtocolId()), (a, b) -> a));
        LocalDate today = LocalDate.now();
        List<ShipmentLotDTO> list = purchaseOrderRepository.findAll().stream()
                .filter(o -> OPEN_PO.contains(o.getStatus()))
                .filter(o -> o.getProtocolId() != null || "PROTOCOL".equals(o.getPurpose()))
                // Chỉ lô hàng phục vụ phác đồ của ngựa người gọi được xem
                .filter(o -> o.getProtocolId() == null ? !isScopedRole(user)
                        : protocols.containsKey(o.getProtocolId()) && canView(user, protocols.get(o.getProtocolId()).getHorse()))
                .sorted(Comparator.comparing(PurchaseOrder::getExpectedDeliveryDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(o -> toShipment(o, protocolCodes, today))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok("Thành công", list));
    }

    private ShipmentLotDTO toShipment(PurchaseOrder o, Map<Integer, String> protocolCodes, LocalDate today) {
        List<PurchaseOrderLine> lines = o.getLines() == null ? List.of() : o.getLines();
        PurchaseOrderLine first = lines.isEmpty() ? null : lines.get(0);
        String itemName = first == null || first.getItem() == null ? "—" : first.getItem().getItemName() + (lines.size() > 1 ? " +" + (lines.size() - 1) : "");
        boolean late = "DELAYED".equals(o.getStatus()) || (o.getExpectedDeliveryDate() != null && o.getExpectedDeliveryDate().isBefore(today));
        return ShipmentLotDTO.builder()
                .lotCode(Objects.toString(o.getOrderCode(), "PO-" + o.getOrderId()))
                .itemName(itemName)
                .quantity(first == null || first.getQuantityOrdered() == null ? 0 : (int) Math.round(first.getQuantityOrdered()))
                .unit(first == null || first.getItem() == null ? "" : first.getItem().getUnit())
                .eta(o.getExpectedDeliveryDate() == null ? "—" : o.getExpectedDeliveryDate().format(DM))
                .targetProtocol(o.getProtocolId() == null ? "—" : protocolCodes.getOrDefault(o.getProtocolId(), "PD-" + o.getProtocolId()))
                .status(late ? "Trễ" : PO_LABEL.getOrDefault(o.getStatus(), o.getStatus()))
                .build();
    }

    private ProtocolDTO toDTO(TreatmentProtocol p) {
        BigDecimal total = p.getPhases() == null ? BigDecimal.ZERO : p.getPhases().stream()
                .flatMap(ph -> ph.getItems().stream())
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String caseCode = p.getCaseId() == null ? "—" : vetCaseRepository.findById(p.getCaseId())
                .map(c -> "#" + Objects.toString(c.getCaseCode(), "C-" + c.getCaseId())).orElse("—");
        String label = STATE_LABEL.getOrDefault(p.getState(), p.getState());
        return ProtocolDTO.builder()
                .protocolCode(Objects.toString(p.getProtocolCode(), "PD-" + p.getProtocolId()) + " v" + (p.getVersionNo() == null ? 1 : p.getVersionNo()))
                .horseName(p.getHorse() != null ? p.getHorse().getHorseName() : "—")
                .diagnosis(p.getDiagnosis())
                .caseCode(caseCode)
                .requiredDate(p.getRequiredBy() == null ? "—" : p.getRequiredBy().format(DMY))
                .estimatedCost(NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(total) + " ₫")
                .status(label)
                .nextStep(p.getNote() != null ? p.getNote() : label)
                .build();
    }

    private BigDecimal lineTotal(ProtocolItem item) {
        if (item.getLineTotal() != null) {
            return item.getLineTotal();
        }
        BigDecimal price = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
        return price.multiply(BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity()));
    }

    private boolean canView(User user, Horse horse) {
        if (user == null || horse == null) {
            return false;
        }
        String role = Objects.toString(user.getRoleName(), "");
        if ("Horse Owner".equals(role)) {
            return horse.getOwner() != null && horse.getOwner().getUserId().equals(user.getUserId());
        }
        if ("Trainer".equals(role)) {
            return horse.getAssignedTrainer() != null && horse.getAssignedTrainer().getUserId().equals(user.getUserId());
        }
        if ("Groom".equals(role)) {
            // Groom chỉ xem ngựa trong khu chuồng mình phụ trách
            String zone = groomProfileRepository.findById(user.getUserId()).map(g -> g.getAssignedBarnZone()).orElse(null);
            String horseZone = horse.getStall() != null ? horse.getStall().getBarnZone() : null;
            return zone != null && horseZone != null && zone.trim().equalsIgnoreCase(horseZone.trim());
        }
        return true;
    }

    // Vai trò chỉ được xem dữ liệu của một số ngựa (không xem lô hàng không gắn phác đồ)
    private static boolean isScopedRole(User user) {
        String role = user == null ? "" : Objects.toString(user.getRoleName(), "");
        return "Horse Owner".equals(role) || "Trainer".equals(role) || "Groom".equals(role) || user == null;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : userRepository.findByUsername(auth.getName()).orElse(null);
    }
}
