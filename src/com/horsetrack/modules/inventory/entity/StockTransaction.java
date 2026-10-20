package com.horsetrack.equinecare.modules.inventory.entity;
import com.horsetrack.equinecare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Nhật ký kho (bảng Stock_Transactions): mỗi lần nhập, xuất, giữ, trả, điều chỉnh đều ghi một dòng.
 * Chỉ thêm mới, không sửa/xóa.
 */
@Entity
@Table(name = "Stock_Transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    // Cột tính toán do DB tự sinh, chỉ đọc
    @Column(name = "tx_code", insertable = false, updatable = false)
    private String txCode;

    @Column(name = "tx_type", nullable = false, length = 20)
    private String txType; // RECEIVE, ISSUE, HOLD, RELEASE, ADJUST, RETURN, DISPOSE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private InventoryLot lot;

    // Với ADJUST có thể âm (giảm) hoặc dương (tăng); các loại khác luôn > 0
    @Column(name = "quantity", nullable = false)
    private Double quantity;

    @Column(name = "balance_after")
    private Double balanceAfter;

    @Column(name = "ref_type", length = 20)
    private String refType; // PURCHASE_ORDER, REQUISITION, PROTOCOL, PRESCRIPTION, TASK, STOCK_COUNT, OTHER

    @Column(name = "ref_id")
    private Integer refId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by", nullable = false)
    private User performedBy;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private LocalDateTime performedAt;

    @Column(name = "note", length = 500)
    private String note;
}
