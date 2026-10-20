package com.horsetrack.equinecare.modules.inventory.entity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Một dòng vật tư trong đơn mua hàng (bảng Purchase_Order_Lines). Mỗi đơn không trùng vật tư (UQ_POL_order_item). */
@Entity
@Table(name = "Purchase_Order_Lines")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrderLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "po_line_id")
    private Integer poLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @Column(name = "quantity_ordered", nullable = false)
    private Double quantityOrdered;

    @Column(name = "quantity_received")
    private Double quantityReceived;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    // Lô và hạn dùng của lần nhận gần nhất
    @Column(name = "lot_no", length = 50)
    private String lotNo;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    public double getRemainingQuantity() {
        double ordered = quantityOrdered != null ? quantityOrdered : 0;
        double received = quantityReceived != null ? quantityReceived : 0;
        return Math.max(0, ordered - received);
    }
}
