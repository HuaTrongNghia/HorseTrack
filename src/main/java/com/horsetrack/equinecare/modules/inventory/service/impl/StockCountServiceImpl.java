package com.horsetrack.equinecare.modules.inventory.service.impl;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.inventory.dto.StockCountDTO;
import com.horsetrack.equinecare.modules.inventory.dto.StockCountRequestDTO;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.entity.StockCount;
import com.horsetrack.equinecare.modules.inventory.entity.StockCountLine;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryItemRepository;
import com.horsetrack.equinecare.modules.inventory.repository.StockCountRepository;
import com.horsetrack.equinecare.modules.inventory.service.StockCountService;
import com.horsetrack.equinecare.modules.inventory.service.StockLedgerService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.horsetrack.equinecare.modules.inventory.service.StockLedgerService.round2;

@Service
@RequiredArgsConstructor
public class StockCountServiceImpl implements StockCountService {

    private final StockCountRepository countRepository;
    private final InventoryItemRepository itemRepository;
    private final StockLedgerService stockLedger;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<StockCountDTO> getCounts() {
        return countRepository.findAllByOrderByCountMonthDesc().stream()
                .map(c -> toDTO(c, false))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public StockCountDTO getCountDetail(Integer countId) {
        return toDTO(findCount(countId), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCountDTO createCount(StockCountRequestDTO request) {
        validateMonth(request.getCountMonth());
        if (countRepository.existsByCountMonth(request.getCountMonth())) {
            throw new IllegalArgumentException("Tháng " + request.getCountMonth() + " đã có phiếu kiểm kê. Hãy cập nhật phiếu đó.");
        }

        StockCount count = StockCount.builder()
                .countMonth(request.getCountMonth())
                .countedBy(stockLedger.currentUser())
                .countedAt(LocalDateTime.now())
                .status("DRAFT")
                .note(StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null)
                .build();
        StockCount saved = countRepository.saveAndFlush(count);
        mergeLines(saved, request.getLines());
        countRepository.saveAndFlush(saved);
        // Đọc lại để lấy count_code do DB tự sinh
        entityManager.refresh(saved);
        return toDTO(saved, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCountDTO updateCount(Integer countId, StockCountRequestDTO request) {
        StockCount count = findCount(countId);
        requireDraft(count);
        validateMonth(request.getCountMonth());
        if (!count.getCountMonth().equals(request.getCountMonth()) && countRepository.existsByCountMonth(request.getCountMonth())) {
            throw new IllegalArgumentException("Tháng " + request.getCountMonth() + " đã có phiếu kiểm kê khác.");
        }
        count.setCountMonth(request.getCountMonth());
        count.setNote(StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null);
        count.setCountedBy(stockLedger.currentUser());
        count.setCountedAt(LocalDateTime.now());
        mergeLines(count, request.getLines());
        countRepository.saveAndFlush(count);
        entityManager.refresh(count);
        return toDTO(count, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCountDTO completeCount(Integer countId) {
        StockCount count = findCount(countId);
        requireDraft(count);

        String note = "Chốt kiểm kê " + count.getCountCode();
        for (StockCountLine line : count.getLines()) {
            Integer itemId = line.getItem().getItemId();
            // Số trên hệ thống lấy tại thời điểm chốt (khóa dòng), vì từ lúc đếm có thể đã có nhập/xuất
            double systemNow = stockLedger.currentStockLocked(itemId);
            line.setSystemQty(systemNow);
            if (round2(line.getCountedQty() - systemNow) != 0 && !StringUtils.hasText(line.getReason())) {
                throw new IllegalArgumentException("Vật tư " + line.getItem().getItemName()
                        + " chênh lệch giữa số đếm và hệ thống nhưng chưa có lý do (reason). Hãy cập nhật phiếu trước khi chốt.");
            }
            stockLedger.adjustTo(itemId, line.getCountedQty(), "STOCK_COUNT", count.getCountId(),
                    line.getReason() != null ? note + ": " + line.getReason() : note);
        }

        count.setStatus("DONE");
        count.setCountedAt(LocalDateTime.now());
        countRepository.saveAndFlush(count);
        entityManager.refresh(count);
        return toDTO(count, true);
    }

    // ==========================================
    // HÀM HỖ TRỢ
    // ==========================================

    private StockCount findCount(Integer countId) {
        return countRepository.findById(countId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu kiểm kê với ID: " + countId));
    }

    private void requireDraft(StockCount count) {
        if (!"DRAFT".equals(count.getStatus())) {
            throw new IllegalArgumentException("Phiếu kiểm kê " + count.getCountCode() + " đã chốt, không thể thay đổi.");
        }
    }

    private void validateMonth(String countMonth) {
        if (YearMonth.parse(countMonth).isAfter(YearMonth.now())) {
            throw new IllegalArgumentException("Không thể kiểm kê cho tháng trong tương lai (" + countMonth + ").");
        }
    }

    /**
     * Cập nhật dòng kiểm kê tại chỗ theo itemId (sửa dòng đã có, thêm dòng mới, xóa dòng không còn).
     * Không xóa hết rồi thêm lại, vì khóa chính ghép (count_id, item_id) sẽ bị trùng khi Hibernate chèn trước khi xóa.
     */
    private void mergeLines(StockCount count, List<StockCountRequestDTO.Line> requestLines) {
        Set<Integer> seen = new HashSet<>();
        for (StockCountRequestDTO.Line l : requestLines) {
            if (!seen.add(l.getItemId())) {
                throw new IllegalArgumentException("Vật tư ID " + l.getItemId() + " bị lặp trong phiếu kiểm kê.");
            }
        }

        Map<Integer, StockCountLine> existing = new HashMap<>();
        count.getLines().forEach(line -> existing.put(line.getItem().getItemId(), line));
        count.getLines().removeIf(line -> !seen.contains(line.getItem().getItemId()));

        for (StockCountRequestDTO.Line l : requestLines) {
            InventoryItem item = itemRepository.findById(l.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vật tư với ID: " + l.getItemId()));
            StockCountLine line = existing.get(l.getItemId());
            if (line == null) {
                line = StockCountLine.builder()
                        .id(new StockCountLine.Id(count.getCountId(), item.getItemId()))
                        .stockCount(count)
                        .item(item)
                        .build();
                count.getLines().add(line);
            }
            // Ảnh chụp tồn kho hệ thống tại lúc nhập số đếm
            line.setSystemQty(item.getCurrentStock() != null ? item.getCurrentStock() : 0.0);
            line.setCountedQty(round2(l.getCountedQty()));
            line.setReason(StringUtils.hasText(l.getReason()) ? l.getReason().trim() : null);
        }
    }

    private StockCountDTO toDTO(StockCount c, boolean includeLines) {
        List<StockCountDTO.Line> lines = c.getLines().stream()
                .map(l -> StockCountDTO.Line.builder()
                        .itemId(l.getItem().getItemId())
                        .itemCode(l.getItem().getItemCode())
                        .itemName(l.getItem().getItemName())
                        .unit(l.getItem().getUnit())
                        .systemQty(l.getSystemQty())
                        .countedQty(l.getCountedQty())
                        .difference(round2(l.getCountedQty() - l.getSystemQty()))
                        .reason(l.getReason())
                        .build())
                .collect(Collectors.toList());
        return StockCountDTO.builder()
                .countId(c.getCountId())
                .countCode(c.getCountCode())
                .countMonth(c.getCountMonth())
                .status(c.getStatus())
                .countedByName(c.getCountedBy() != null ? c.getCountedBy().getFullName() : null)
                .countedAt(c.getCountedAt())
                .note(c.getNote())
                .totalLines(lines.size())
                .linesWithDifference((int) lines.stream().filter(l -> l.getDifference() != 0).count())
                .lines(includeLines ? lines : null)
                .build();
    }
}
