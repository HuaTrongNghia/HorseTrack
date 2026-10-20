package com.horsetrack.equinecare.modules.inventory.entity;
import com.horsetrack.equinecare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Đơn mua hàng (bảng Purchase_Orders); từng vật tư nằm ở Purchase_Order_Lines.
 * Ràng buộc DB: purpose = PROTOCOL thì phải có protocol_id, REQUISITION thì phải có requisition_id.
 */
@Entity
@Table(name = "Purchase_Orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Integer orderId;

    // Cột tính toán do DB tự sinh (SHIP-0001), chỉ đọc
    @Column(name = "order_code", insertable = false, updatable = false)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "purpose", nullable = false, length = 20)
    private String purpose; // RESTOCK, REQUISITION, PROTOCOL

    @Column(name = "status", nullable = false, length = 20)
    private String status; // ORDERED, IN_TRANSIT, DELAYED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "previous_eta")
    private LocalDate previousEta;

    @Column(name = "required_by")
    private LocalDate requiredBy;

    @Column(name = "ordered_at", nullable = false, updatable = false)
    private LocalDateTime orderedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "protocol_id")
    private Integer protocolId;

    @Column(name = "requisition_id")
    private Integer requisitionId;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("poLineId ASC")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    public void addLine(PurchaseOrderLine line) {
        lines.add(line);
        line.setOrder(this);
    }
}
