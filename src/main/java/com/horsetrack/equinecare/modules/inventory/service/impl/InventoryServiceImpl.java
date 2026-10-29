package com.horsetrack.equinecare.modules.inventory.service.impl;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.inventory.dto.*;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.entity.StockTransaction;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryItemRepository;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryLotRepository;
import com.horsetrack.equinecare.modules.inventory.repository.StockTransactionRepository;
import com.horsetrack.equinecare.modules.inventory.service.InventoryMapper;
import com.horsetrack.equinecare.modules.inventory.service.InventoryService;
import com.horsetrack.equinecare.modules.inventory.service.StockLedgerService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.horsetrack.equinecare.modules.inventory.service.StockLedgerService.round2;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository itemRepository;
    private final InventoryLotRepository lotRepository;
    private final StockTransactionRepository transactionRepository;
    private final StockLedgerService stockLedger;
    private final EntityManager entityManager;

    // ==========================================
    // DANH MỤC VẬT TƯ & TỒN KHO
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemDTO> getItems(String category, String keyword) {
        // Nhận cả mã (MEDICINE) lẫn nhãn (Thuốc); tìm cả bản ghi lưu nhãn và bản ghi cũ lưu mã
        String cat = StringUtils.hasText(category) ? com.horsetrack.equinecare.modules.inventory.service.ItemCategories.label(category) : null;
        String code = StringUtils.hasText(category) ? com.horsetrack.equinecare.modules.inventory.service.ItemCategories.code(category) : null;
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return itemRepository.search(cat, code != null ? code : cat, kw).stream()
                .map(item -> InventoryMapper.toItemDTO(item, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItemDTO getItemDetail(Integer itemId) {
        InventoryItem item = findItem(itemId);
        return InventoryMapper.toItemDTO(item, lotRepository.findByItem_ItemIdOrderByExpiryDateAscLotIdAsc(itemId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryItemDTO createItem(InventoryItemRequestDTO request) {
        String category = normalizeCategory(request.getCategory());
        if (itemRepository.existsByItemNameIgnoreCaseAndCategory(request.getItemName().trim(), category)) {
            throw new IllegalArgumentException("Vật tư '" + request.getItemName().trim() + "' đã tồn tại trong nhóm " + category + ".");
        }

        InventoryItem item = InventoryItem.builder()
                .currentStock(0.0)
                .heldStock(0.0)
                .lastRestockedAt(LocalDateTime.now())
                .build();
        applyItemInfo(item, request);

        InventoryItem saved = itemRepository.saveAndFlush(item);
        // Đọc lại từ DB để lấy item_code do DB tự sinh
        entityManager.refresh(saved);
        return InventoryMapper.toItemDTO(saved, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryItemDTO updateItem(Integer itemId, InventoryItemRequestDTO request) {
        InventoryItem item = findItem(itemId);
        applyItemInfo(item, request);
        return InventoryMapper.toItemDTO(itemRepository.save(item), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryItemDTO updateSafetyLevel(Integer itemId, SafetyLevelRequestDTO request) {
        InventoryItem item = findItem(itemId);
        item.setMinSafetyStock(round2(request.getMinSafetyStock()));
        return InventoryMapper.toItemDTO(itemRepository.save(item), null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemDTO> getLowStockItems() {
        return itemRepository.findBelowSafetyLevel().stream()
                .map(item -> InventoryMapper.toItemDTO(item, null))
                .collect(Collectors.toList());
    }

    // ==========================================
    // NHẬP / XUẤT / ĐIỀU CHỈNH KHO (thủ công)
    // ==========================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockMovementResponseDTO recordMovement(StockMovementRequestDTO request) {
        String type = request.getMovementType().toUpperCase();
        double quantity = round2(request.getQuantity());
        String lotNo = StringUtils.hasText(request.getLotNo()) ? request.getLotNo().trim() : null;
        String note = StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null;

        if (("ADJUST".equals(type) || "DISPOSE".equals(type)) && note == null) {
            throw new IllegalArgumentException("Phải nhập lý do (note) khi điều chỉnh hoặc hủy hàng.");
        }

        StockTransaction tx = switch (type) {
            case "RECEIVE" -> stockLedger.receive(request.getItemId(), quantity, lotNo, request.getExpiryDate(),
                    null, "OTHER", null, note);
            case "ISSUE", "DISPOSE" -> stockLedger.issue(request.getItemId(), quantity, lotNo, type, "OTHER", null, note);
            case "ADJUST" -> stockLedger.adjust(request.getItemId(), quantity, lotNo, "OTHER", null, note);
            default -> throw new IllegalArgumentException("Loại phát sinh không hợp lệ: " + type);
        };

        InventoryItem item = tx.getItem();
        return StockMovementResponseDTO.builder()
                .transaction(InventoryMapper.toTransactionDTO(tx))
                .item(InventoryMapper.toItemDTO(item, null))
                .lowStockWarning(InventoryMapper.lowStockWarning(item))
                .build();
    }

    // ==========================================
    // NHẬT KÝ KHO
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public Page<StockTransactionDTO> getTransactions(Integer itemId, String txType, LocalDate fromDate, LocalDate toDate, int page, int size) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate không được nhỏ hơn fromDate.");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 500);
        Sort newest = Sort.by(Sort.Order.desc("performedAt"), Sort.Order.desc("transactionId"));
        if (itemId == null && !StringUtils.hasText(txType) && fromDate == null && toDate == null) {
            return transactionRepository.findAll(PageRequest.of(safePage, safeSize, newest))
                    .map(InventoryMapper::toTransactionDTO);
        }
        Integer item = itemId != null ? itemId : -1;
        String type = StringUtils.hasText(txType) ? txType.trim().toUpperCase() : "";
        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime to = toDate != null ? toDate.plusDays(1).atStartOfDay() : LocalDateTime.of(9999, 1, 1, 0, 0);
        return transactionRepository.search(item, type, from, to, PageRequest.of(safePage, safeSize))
                .map(InventoryMapper::toTransactionDTO);
    }

    // ==========================================
    // HÀM HỖ TRỢ
    // ==========================================

    private InventoryItem findItem(Integer itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vật tư với ID: " + itemId));
    }

    // Lưu nhãn tiếng Việt chuẩn (khớp dữ liệu hiện có); nhận cả mã tiếng Anh hoặc nhãn
    private static String normalizeCategory(String category) {
        if (com.horsetrack.equinecare.modules.inventory.service.ItemCategories.code(category) == null) {
            throw new IllegalArgumentException("Nhóm vật tư '" + category + "' không hợp lệ. Dùng một trong: "
                    + String.join(", ", com.horsetrack.equinecare.modules.inventory.service.ItemCategories.LABELS.values()) + " (hoặc mã " + String.join(", ", com.horsetrack.equinecare.modules.inventory.service.ItemCategories.LABELS.keySet()) + ").");
        }
        return com.horsetrack.equinecare.modules.inventory.service.ItemCategories.label(category);
    }

    private void applyItemInfo(InventoryItem item, InventoryItemRequestDTO request) {
        item.setItemName(request.getItemName().trim());
        item.setCategory(normalizeCategory(request.getCategory()));
        item.setUnit(request.getUnit().trim());
        item.setUnitPrice(request.getUnitPrice());
        item.setMinSafetyStock(round2(request.getMinSafetyStock()));
        item.setItemGroup(request.getItemGroup());
        item.setBrand(request.getBrand());
        item.setRoute(request.getRoute());
        item.setIsControlled(Boolean.TRUE.equals(request.getIsControlled()));
        item.setRequiresPrescription(Boolean.TRUE.equals(request.getRequiresPrescription()));
        item.setWithdrawalHours(request.getWithdrawalHours());
        item.setDoseReference(request.getDoseReference());
        item.setNote(request.getNote());
    }
}
