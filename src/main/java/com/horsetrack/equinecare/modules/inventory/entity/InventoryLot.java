package com.horsetrack.equinecare.modules.inventory.entity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lô hàng của một vật tư (bảng Inventory_Lots). Mỗi vật tư không được trùng số lô (UQ_IL_item_lot).
 */
@Entity
@Table(name = "Inventory_Lots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryLot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lot_id")
    private Integer lotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @Column(name = "lot_no", nullable = false, length = 50)
    private String lotNo;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "quantity", nullable = false)
    private Double quantity;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "po_line_id")
    private Integer poLineId;
}
