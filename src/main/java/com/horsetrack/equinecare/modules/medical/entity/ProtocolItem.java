package com.horsetrack.equinecare.modules.medical.entity;

import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Một thuốc/vật tư y tế trong giai đoạn điều trị (bảng Protocol_Items). Mỗi giai đoạn không trùng vật tư. */
@Entity
@Table(name = "Protocol_Items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProtocolItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "protocol_item_id")
    private Integer protocolItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", nullable = false)
    private ProtocolPhase phase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @Column(name = "quantity", nullable = false)
    private Double quantity;

    // Đơn giá tại thời điểm lập phác đồ
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "dosage", length = 100)
    private String dosage;

    // Cột tính toán do DB tự sinh (quantity * unit_price), chỉ đọc
    @Column(name = "line_total", insertable = false, updatable = false)
    private BigDecimal lineTotal;
}
