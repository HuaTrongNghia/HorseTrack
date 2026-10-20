package com.horsetrack.equinecare.modules.inventory.entity;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/** Một dòng kiểm kê (bảng Stock_Count_Lines), khóa chính ghép (count_id, item_id). */
@Entity
@Table(name = "Stock_Count_Lines")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockCountLine {

    @EmbeddedId
    private Id id;

    @MapsId("countId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "count_id")
    private StockCount stockCount;

    @MapsId("itemId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private InventoryItem item;

    // Tồn kho trên hệ thống tại thời điểm đếm
    @Column(name = "system_qty", nullable = false)
    private Double systemQty;

    @Column(name = "counted_qty", nullable = false)
    private Double countedQty;

    // Cột tính toán do DB tự sinh (counted_qty - system_qty), chỉ đọc
    @Column(name = "difference", insertable = false, updatable = false)
    private Double difference;

    @Column(name = "reason", length = 255)
    private String reason;

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Id implements Serializable {
        @Column(name = "count_id")
        private Integer countId;

        @Column(name = "item_id")
        private Integer itemId;
    }
}
