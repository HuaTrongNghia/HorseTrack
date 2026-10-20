package com.horsetrack.equinecare.modules.inventory.entity;
import com.horsetrack.equinecare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Yêu cầu vật tư của Groom gửi kho (bảng Material_Requisitions).
 * Luồng trạng thái: SUBMITTED -> (WAITING_RESTOCK -> CHECKING_STOCK) -> ISSUED | REJECTED | CANCELLED.
 */
@Entity
@Table(name = "Material_Requisitions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MaterialRequisition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "requisition_id")
    private Integer requisitionId;

    // Cột tính toán do DB tự sinh (RQ-0001), chỉ đọc
    @Column(name = "requisition_code", insertable = false, updatable = false)
    private String requisitionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "barn_zone", length = 50)
    private String barnZone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @Column(name = "quantity", nullable = false)
    private Double quantity;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "needed_by")
    private LocalDate neededBy;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // SUBMITTED, CHECKING_STOCK, WAITING_RESTOCK, ISSUED, REJECTED, CANCELLED

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "issued_quantity")
    private Double issuedQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_to")
    private User issuedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by")
    private User issuedBy;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;
}
