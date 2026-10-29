package com.horsetrack.equinecare.modules.inventory.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryLot;
import com.horsetrack.equinecare.modules.inventory.entity.StockTransaction;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryItemRepository;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryLotRepository;
import com.horsetrack.equinecare.modules.inventory.repository.StockTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sổ cái kho: nơi DUY NHẤT thay đổi tồn kho.
 * Mỗi lần nhập/xuất/điều chỉnh đều khóa dòng vật tư, cập nhật lô và ghi một dòng Stock_Transactions.
 * Luôn chạy bên trong transaction của service gọi tới (đơn mua hàng, yêu cầu vật tư, kiểm kê...).
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class StockLedgerService {

    private final InventoryItemRepository itemRepository;
    private final InventoryLotRepository lotRepository;
    private final StockTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    /** Nhập kho vào một lô (tạo lô mới nếu chưa có). */
    public StockTransaction receive(Integer itemId, double quantity, String lotNo, LocalDate expiryDate,
                                    Integer poLineId, String refType, Integer refId, String note) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng nhập phải lớn hơn 0.");
        }
        InventoryItem item = lockItem(itemId);
        // Không nhập số lô thì tự sinh (Inventory_Lots bắt buộc có số lô, duy nhất theo vật tư)
        if (lotNo == null || lotNo.isBlank()) {
            lotNo = autoLotNo(itemId);
        } else {
            lotNo = lotNo.trim();
        }

        InventoryLot lot = lotRepository.findByItem_ItemIdAndLotNo(itemId, lotNo).orElse(null);
        if (lot != null) {
            entityManager.refresh(lot);
        }
        if (lot == null) {
            lot = InventoryLot.builder()
                    .item(item)
                    .lotNo(lotNo)
                    .expiryDate(expiryDate)
                    .quantity(0.0)
                    .receivedAt(LocalDateTime.now())
                    .poLineId(poLineId)
                    .build();
        } else if (expiryDate != null && lot.getExpiryDate() != null && !expiryDate.equals(lot.getExpiryDate())) {
            throw new IllegalArgumentException("Lô " + lotNo + " của " + item.getItemName() + " đã có hạn dùng "
                    + lot.getExpiryDate() + ", khác với hạn dùng vừa nhập. Hãy dùng số lô khác.");
        }
        lot.setQuantity(round2(lot.getQuantity() + quantity));
        lot = lotRepository.save(lot);

        item.setLastRestockedAt(LocalDateTime.now());
        return applyChange(item, lot, "RECEIVE", quantity, quantity, refType, refId, note);
    }

    /** Xuất kho (ISSUE) hoặc hủy hàng (DISPOSE). Không được xuất phần đang giữ cho phác đồ. */
    public StockTransaction issue(Integer itemId, double quantity, String lotNo, String txType,
                                  String refType, Integer refId, String note) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng xuất phải lớn hơn 0.");
        }
        InventoryItem item = lockItem(itemId);
        double onHand = value(item.getCurrentStock());
        double held = value(item.getHeldStock());
        double available = onHand - held;
        if (quantity > available) {
            throw new IllegalArgumentException(String.format(
                    "Không đủ hàng khả dụng của %s. Đang có: %s, đang giữ: %s, khả dụng: %s, yêu cầu: %s.",
                    item.getItemName(), fmt(onHand), fmt(held), fmt(available), fmt(quantity)));
        }

        InventoryLot lot = null;
        if (lotNo != null && !lotNo.isBlank()) {
            lot = findLot(item, lotNo);
            if (quantity > value(lot.getQuantity())) {
                throw new IllegalArgumentException("Lô " + lotNo + " chỉ còn " + fmt(lot.getQuantity())
                        + ", không đủ để xuất " + fmt(quantity) + ".");
            }
            lot.setQuantity(round2(lot.getQuantity() - quantity));
            lot = lotRepository.save(lot);
        }
        return applyChange(item, lot, txType, quantity, -quantity, refType, refId, note);
    }

    /** Nhập lại kho phần đã cấp nhưng chưa dùng (RETURN), không gắn lô. */
    public StockTransaction returnStock(Integer itemId, double quantity, String refType, Integer refId, String note) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng nhập lại phải lớn hơn 0.");
        }
        InventoryItem item = lockItem(itemId);
        return applyChange(item, null, "RETURN", quantity, quantity, refType, refId, note);
    }

    /** Điều chỉnh tồn kho: delta dương để tăng, âm để giảm. */
    public StockTransaction adjust(Integer itemId, double delta, String lotNo,
                                   String refType, Integer refId, String note) {
        if (delta == 0) {
            throw new IllegalArgumentException("Số lượng điều chỉnh phải khác 0 (dương để tăng, âm để giảm).");
        }
        InventoryItem item = lockItem(itemId);
        double onHand = value(item.getCurrentStock());
        double held = value(item.getHeldStock());
        double newOnHand = onHand + delta;
        if (newOnHand < 0) {
            throw new IllegalArgumentException("Điều chỉnh làm tồn kho của " + item.getItemName() + " bị âm (đang có " + fmt(onHand) + ").");
        }
        if (newOnHand < held) {
            throw new IllegalArgumentException("Tồn kho sau điều chỉnh của " + item.getItemName() + " (" + fmt(newOnHand)
                    + ") không được nhỏ hơn số lượng đang giữ cho phác đồ (" + fmt(held) + ").");
        }

        InventoryLot lot = null;
        if (lotNo != null && !lotNo.isBlank()) {
            lot = findLot(item, lotNo);
            double newLotQty = value(lot.getQuantity()) + delta;
            if (newLotQty < 0) {
                throw new IllegalArgumentException("Điều chỉnh làm lô " + lotNo + " bị âm (lô còn " + fmt(lot.getQuantity()) + ").");
            }
            lot.setQuantity(round2(newLotQty));
            lot = lotRepository.save(lot);
        }
        return applyChange(item, lot, "ADJUST", delta, delta, refType, refId, note);
    }

    // ==========================================
    // GIỮ HÀNG CHO PHÁC ĐỒ (Stock_Holds)
    // ==========================================

    /** Khả dụng (đang có - đang giữ) của vật tư, đã khóa dòng. */
    public double availableLocked(Integer itemId) {
        InventoryItem item = lockItem(itemId);
        return value(item.getCurrentStock()) - value(item.getHeldStock());
    }

    /** Giữ hàng cho phác đồ: tăng held_stock, tồn kho đang có không đổi. */
    public StockTransaction hold(Integer itemId, double quantity, String refType, Integer refId, String note) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng giữ phải lớn hơn 0.");
        }
        InventoryItem item = lockItem(itemId);
        double available = value(item.getCurrentStock()) - value(item.getHeldStock());
        if (quantity > available) {
            throw new IllegalArgumentException(String.format("Không đủ hàng khả dụng của %s để giữ. Khả dụng: %s, cần giữ: %s.",
                    item.getItemName(), fmt(available), fmt(quantity)));
        }
        item.setHeldStock(round2(value(item.getHeldStock()) + quantity));
        return applyChange(item, null, "HOLD", quantity, 0, refType, refId, note);
    }

    /** Trả lại phần đã giữ (khi hủy phác đồ hoặc tạo phiên bản mới): giảm held_stock. */
    public StockTransaction release(Integer itemId, double quantity, String refType, Integer refId, String note) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng trả lại phải lớn hơn 0.");
        }
        InventoryItem item = lockItem(itemId);
        item.setHeldStock(round2(Math.max(0, value(item.getHeldStock()) - quantity)));
        return applyChange(item, null, "RELEASE", quantity, 0, refType, refId, note);
    }

    /** Cấp phần đã giữ: giảm cả held_stock và tồn kho đang có. */
    public StockTransaction issueHeld(Integer itemId, double quantity, String refType, Integer refId, String note) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng cấp phải lớn hơn 0.");
        }
        InventoryItem item = lockItem(itemId);
        double held = value(item.getHeldStock());
        double onHand = value(item.getCurrentStock());
        if (quantity > held + 0.001 || quantity > onHand + 0.001) {
            throw new IllegalArgumentException(String.format("Số lượng đã giữ của %s không khớp (đang giữ %s, đang có %s, cần cấp %s).",
                    item.getItemName(), fmt(held), fmt(onHand), fmt(quantity)));
        }
        // Giảm held trước để giữ ràng buộc held_stock <= current_stock
        item.setHeldStock(round2(held - quantity));
        return applyChange(item, null, "ISSUE", quantity, -quantity, refType, refId, note);
    }

    /**
     * Cấp hàng đã giữ, trừ lô theo hạn dùng gần nhất trước (FEFO; lô không có hạn dùng sau cùng).
     * Mỗi lô ghi một dòng ISSUE kèm số lô; phần vượt số lượng các lô đang ghi nhận thì ghi một dòng không có lô.
     */
    public java.util.List<StockTransaction> issueHeldFefo(Integer itemId, double quantity, String refType, Integer refId, String note) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng cấp phải lớn hơn 0.");
        }
        InventoryItem item = lockItem(itemId);
        double held = value(item.getHeldStock());
        double onHand = value(item.getCurrentStock());
        if (quantity > held + 0.001 || quantity > onHand + 0.001) {
            throw new IllegalArgumentException(String.format("Số lượng đã giữ của %s không khớp (đang giữ %s, đang có %s, cần cấp %s).",
                    item.getItemName(), fmt(held), fmt(onHand), fmt(quantity)));
        }
        item.setHeldStock(round2(held - quantity));
        java.util.List<InventoryLot> lots = lotRepository.findByItem_ItemIdOrderByExpiryDateAscLotIdAsc(itemId).stream()
                .filter(l -> value(l.getQuantity()) > 0)
                .sorted(java.util.Comparator.comparing(InventoryLot::getExpiryDate, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(InventoryLot::getLotId))
                .toList();
        java.util.List<StockTransaction> result = new java.util.ArrayList<>();
        double remaining = round2(quantity);
        for (InventoryLot lot : lots) {
            if (remaining <= 0) {
                break;
            }
            double take = round2(Math.min(remaining, value(lot.getQuantity())));
            lot.setQuantity(round2(lot.getQuantity() - take));
            lotRepository.save(lot);
            result.add(applyChange(item, lot, "ISSUE", take, -take, refType, refId, note));
            remaining = round2(remaining - take);
        }
        if (remaining > 0) {
            result.add(applyChange(item, null, "ISSUE", remaining, -remaining, refType, refId, note));
        }
        return result;
    }

    /**
     * Đặt tồn kho về đúng số lượng thực tế (dùng khi chốt kiểm kê).
     * Khóa dòng rồi mới tính chênh lệch so với tồn kho hiện tại; trả về null nếu không chênh lệch.
     */
    public StockTransaction adjustTo(Integer itemId, double targetQuantity, String refType, Integer refId, String note) {
        InventoryItem item = lockItem(itemId);
        double delta = round2(targetQuantity - value(item.getCurrentStock()));
        if (delta == 0) {
            return null;
        }
        return adjust(itemId, delta, null, refType, refId, note);
    }

    /** Tồn kho hiện tại của vật tư (đã khóa dòng trong transaction hiện tại). */
    public double currentStockLocked(Integer itemId) {
        return value(lockItem(itemId).getCurrentStock());
    }

    // ==========================================
    // HÀM HỖ TRỢ
    // ==========================================

    private StockTransaction applyChange(InventoryItem item, InventoryLot lot, String txType, double txQuantity,
                                         double stockChange, String refType, Integer refId, String note) {
        double balanceAfter = round2(value(item.getCurrentStock()) + stockChange);
        item.setCurrentStock(balanceAfter);
        itemRepository.save(item);

        StockTransaction tx = StockTransaction.builder()
                .txType(txType)
                .item(item)
                .lot(lot)
                .quantity(round2(txQuantity))
                .balanceAfter(balanceAfter)
                .refType(refType != null ? refType : "OTHER")
                .refId(refId)
                .performedBy(currentUser())
                .performedAt(LocalDateTime.now())
                .note(note)
                .build();
        StockTransaction saved = transactionRepository.saveAndFlush(tx);
        // Đọc lại để lấy tx_code do DB tự sinh
        entityManager.refresh(saved);
        return saved;
    }

    // Khóa dòng vật tư để các phát sinh đồng thời không ghi đè tồn kho của nhau.
    // Dùng refresh kèm khóa để luôn đọc lại số mới nhất từ DB, kể cả khi entity đã nằm sẵn trong phiên làm việc.
    private InventoryItem lockItem(Integer itemId) {
        InventoryItem item = entityManager.find(InventoryItem.class, itemId);
        if (item == null) {
            throw new ResourceNotFoundException("Không tìm thấy vật tư với ID: " + itemId);
        }
        entityManager.refresh(item, LockModeType.PESSIMISTIC_WRITE);
        return item;
    }

    // Gọi sau khi đã khóa vật tư; đọc lại lô để có số lượng mới nhất
    // Số lô tự sinh: AUTO-yyMMdd-HHmmssSSS, thêm hậu tố nếu trùng
    private String autoLotNo(Integer itemId) {
        String base = "AUTO-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd-HHmmssSSS"));
        String lotNo = base;
        for (int i = 2; lotRepository.findByItem_ItemIdAndLotNo(itemId, lotNo).isPresent(); i++) {
            lotNo = base + "-" + i;
        }
        return lotNo;
    }

    private InventoryLot findLot(InventoryItem item, String lotNo) {
        InventoryLot lot = lotRepository.findByItem_ItemIdAndLotNo(item.getItemId(), lotNo)
                .orElseThrow(() -> new ResourceNotFoundException("Vật tư " + item.getItemName() + " không có lô " + lotNo + "."));
        entityManager.refresh(lot);
        return lot;
    }

    public User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }

    private static double value(Double v) {
        return v != null ? v : 0;
    }

    // Làm tròn 2 chữ số thập phân cho khớp kiểu decimal(10,2) của DB và tránh sai số số thực
    public static double round2(Double value) {
        if (value == null) return 0;
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static String fmt(Double value) {
        if (value == null) return "0";
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
