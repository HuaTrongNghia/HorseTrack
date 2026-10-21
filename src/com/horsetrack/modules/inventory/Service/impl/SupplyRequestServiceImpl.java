package com.horsetrack.equinecare.modules.inventory.service.impl;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.entity.GroomProfile;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.GroomProfileRepository;
import com.horsetrack.equinecare.modules.inventory.dto.*;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.entity.MaterialRequisition;
import com.horsetrack.equinecare.modules.inventory.entity.PurchaseOrder;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryItemRepository;
import com.horsetrack.equinecare.modules.inventory.repository.MaterialRequisitionRepository;
import com.horsetrack.equinecare.modules.inventory.repository.PurchaseOrderRepository;
import com.horsetrack.equinecare.modules.inventory.service.InventoryMapper;
import com.horsetrack.equinecare.modules.inventory.service.StockLedgerService;
import com.horsetrack.equinecare.modules.inventory.service.SupplyRequestService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.horsetrack.equinecare.modules.inventory.service.StockLedgerService.fmt;
import static com.horsetrack.equinecare.modules.inventory.service.StockLedgerService.round2;

@Service
@RequiredArgsConstructor
public class SupplyRequestServiceImpl implements SupplyRequestService {

    // Yêu cầu chưa xử lý xong
    private static final Set<String> OPEN_STATUSES = Set.of("SUBMITTED", "CHECKING_STOCK", "WAITING_RESTOCK");
    // Groom chỉ được yêu cầu vật tư thông thường; thuốc và vaccine đi theo phác đồ của bác sĩ
    private static final Set<String> MEDICAL_CATEGORIES = Set.of("MEDICINE", "VACCINE");

    private final MaterialRequisitionRepository requisitionRepository;
    private final InventoryItemRepository itemRepository;
    private final PurchaseOrderRepository orderRepository;
    private final GroomProfileRepository groomProfileRepository;
    private final StockLedgerService stockLedger;
    private final EntityManager entityManager;

    // ==========================================
    // GROOM
    // ==========================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplyRequestDTO createRequest(SupplyRequestCreateDTO request) {
        User groom = stockLedger.currentUser();
        InventoryItem item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vật tư với ID: " + request.getItemId()));
        if (MEDICAL_CATEGORIES.contains(item.getCategory())) {
            throw new IllegalArgumentException("Groom chỉ yêu cầu được vật tư thông thường. Thuốc và vaccine ("
                    + item.getItemName() + ") phải đi theo phác đồ của bác sĩ thú y.");
        }

        String barnZone = groomProfileRepository.findById(groom.getUserId())
                .map(GroomProfile::getAssignedBarnZone)
                .orElse(null);

        MaterialRequisition req = MaterialRequisition.builder()
                .requestedBy(groom)
                .barnZone(barnZone)
                .item(item)
                .quantity(round2(request.getQuantity()))
                .reason(request.getReason().trim())
                .requestedAt(LocalDateTime.now())
                .neededBy(request.getNeededBy())
                .status("SUBMITTED")
                .build();
        MaterialRequisition saved = requisitionRepository.saveAndFlush(req);
        // Đọc lại để lấy requisition_code do DB tự sinh
        entityManager.refresh(saved);
        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplyRequestDTO> getMyRequests() {
        User groom = stockLedger.currentUser();
        return requisitionRepository.findByRequestedBy_UserIdOrderByRequestedAtDesc(groom.getUserId()).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemDTO> getRequestableItems() {
        return itemRepository.findByCategoryNotInOrderByItemNameAsc(MEDICAL_CATEGORIES).stream()
                .map(item -> InventoryMapper.toItemDTO(item, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplyRequestDTO cancelMyRequest(Integer requisitionId) {
        User groom = stockLedger.currentUser();
        MaterialRequisition req = findRequest(requisitionId);
        if (!req.getRequestedBy().getUserId().equals(groom.getUserId())) {
            throw new AccessDeniedException("Bạn chỉ được hủy yêu cầu vật tư của chính mình");
        }
        requireOpen(req, "hủy");
        req.setStatus("CANCELLED");
        return toDTO(requisitionRepository.save(req));
    }

    // ==========================================
    // NHÂN VIÊN KHO
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public List<SupplyRequestDTO> getRequests(String status, String barnZone) {
        String st = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        String zone = StringUtils.hasText(barnZone) ? barnZone.trim() : null;
        return requisitionRepository.search(st, zone).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplyRequestDTO issue(Integer requisitionId, SupplyRequestIssueDTO request) {
        MaterialRequisition req = findRequest(requisitionId);
        requireOpen(req, "cấp vật tư cho");

        double requested = req.getQuantity();
        double qty = request != null && request.getIssuedQuantity() != null ? round2(request.getIssuedQuantity()) : requested;
        if (qty > requested) {
            throw new IllegalArgumentException("Số lượng cấp (" + fmt(qty) + ") không được lớn hơn số lượng yêu cầu (" + fmt(requested) + ").");
        }
        String lotNo = request != null && StringUtils.hasText(request.getLotNo()) ? request.getLotNo().trim() : null;
        String note = request != null && StringUtils.hasText(request.getNote())
                ? request.getNote().trim()
                : "Cấp vật tư cho yêu cầu " + req.getRequisitionCode();

        try {
            // Xuất kho qua sổ cái; không đủ khả dụng thì sổ cái báo lỗi
            stockLedger.issue(req.getItem().getItemId(), qty, lotNo, "ISSUE", "REQUISITION", req.getRequisitionId(), note);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(ex.getMessage()
                    + " Hãy chuyển yêu cầu sang chờ hàng (wait-restock) hoặc tạo đơn mua hàng.");
        }

        User warehouse = stockLedger.currentUser();
        req.setStatus("ISSUED");
        req.setIssuedQuantity(qty);
        req.setIssuedTo(req.getRequestedBy());
        req.setIssuedBy(warehouse);
        req.setIssuedAt(LocalDateTime.now());
        return toDTO(requisitionRepository.save(req));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplyRequestDTO markWaitingRestock(Integer requisitionId) {
        MaterialRequisition req = findRequest(requisitionId);
        if (!"SUBMITTED".equals(req.getStatus()) && !"CHECKING_STOCK".equals(req.getStatus())) {
            throw new IllegalArgumentException("Chỉ yêu cầu SUBMITTED hoặc CHECKING_STOCK mới chuyển sang chờ hàng (hiện tại: " + req.getStatus() + ").");
        }
        req.setStatus("WAITING_RESTOCK");
        return toDTO(requisitionRepository.save(req));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplyRequestDTO reject(Integer requisitionId, ReasonRequestDTO request) {
        MaterialRequisition req = findRequest(requisitionId);
        requireOpen(req, "từ chối");
        req.setStatus("REJECTED");
        req.setRejectReason(request.getReason().trim());
        return toDTO(requisitionRepository.save(req));
    }

    // ==========================================
    // HÀM HỖ TRỢ
    // ==========================================

    private MaterialRequisition findRequest(Integer requisitionId) {
        return requisitionRepository.findById(requisitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu vật tư với ID: " + requisitionId));
    }

    private void requireOpen(MaterialRequisition req, String action) {
        if (!OPEN_STATUSES.contains(req.getStatus())) {
            throw new IllegalArgumentException("Không thể " + action + " yêu cầu " + req.getRequisitionCode()
                    + " vì đang ở trạng thái " + req.getStatus() + ".");
        }
    }

    private SupplyRequestDTO toDTO(MaterialRequisition r) {
        InventoryItem item = r.getItem();
        List<String> poCodes = orderRepository.findByRequisitionId(r.getRequisitionId()).stream()
                .map(PurchaseOrder::getOrderCode)
                .collect(Collectors.toList());
        return SupplyRequestDTO.builder()
                .requisitionId(r.getRequisitionId())
                .requisitionCode(r.getRequisitionCode())
                .requestedById(r.getRequestedBy().getUserId())
                .requestedByName(r.getRequestedBy().getFullName())
                .barnZone(r.getBarnZone())
                .itemId(item.getItemId())
                .itemCode(item.getItemCode())
                .itemName(item.getItemName())
                .unit(item.getUnit())
                .quantity(r.getQuantity())
                .availableStock(round2(item.getAvailableStock()))
                .reason(r.getReason())
                .requestedAt(r.getRequestedAt())
                .neededBy(r.getNeededBy())
                .status(r.getStatus())
                .rejectReason(r.getRejectReason())
                .issuedQuantity(r.getIssuedQuantity())
                .issuedByName(r.getIssuedBy() != null ? r.getIssuedBy().getFullName() : null)
                .issuedAt(r.getIssuedAt())
                .purchaseOrderCodes(poCodes)
                .build();
    }
}
