package com.horsetrack.equinecare.modules.inventory.entity;
import com.horsetrack.equinecare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Kết quả kiểm kho của một vật tư trong phác đồ (bảng Stock_Holds).
 * HELD: đã giữ đủ; SHORT: thiếu (giữ được một phần hoặc không có); RELEASED: đã trả lại; ISSUED: đã cấp.
 */
@Entity
@Table(name = "Stock_Holds")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockHold {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hold_id")
    private Integer holdId;

    @Column(name = "protocol_id", nullable = false)
    private Integer protocolId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private InventoryLot lot;

    @Column(name = "qty_needed", nullable = false)
    private Double qtyNeeded;

    // Khả dụng tại lúc kiểm kho
    @Column(name = "qty_available", nullable = false)
    private Double qtyAvailable;

    @Column(name = "qty_held", nullable = false)
    private Double qtyHeld;

    // Cột tính toán do DB tự sinh (qty_needed - qty_held nếu thiếu), chỉ đọc
    @Column(name = "qty_short", insertable = false, updatable = false)
    private Double qtyShort;

    @Column(name = "eta")
    private LocalDate eta;

    @Column(name = "purchase_order_id")
    private Integer purchaseOrderId;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // HELD, SHORT, RELEASED, ISSUED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by", nullable = false)
    private User checkedBy;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;
}
