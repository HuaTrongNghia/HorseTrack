package com.horsetrack.equinecare.modules.inventory.entity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vật tư trong kho (bảng Inventory_Items).
 * Khả dụng = currentStock (đang có) - heldStock (đang giữ cho phác đồ).
 */
@Entity
@Table(name = "Inventory_Items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Integer itemId;

    // Cột tính toán do DB tự sinh (VT-0001), chỉ đọc
    @Column(name = "item_code", insertable = false, updatable = false)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "category", nullable = false, length = 30)
    private String category; // FEED, SUPPLEMENT, MEDICINE, VACCINE, MEDICAL_SUPPLY, STABLE_SUPPLY, EQUIPMENT, OTHER

    @Column(name = "item_group", length = 50)
    private String itemGroup;

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "current_stock", nullable = false)
    private Double currentStock;

    @Column(name = "held_stock", nullable = false)
    private Double heldStock;

    @Column(name = "min_safety_stock", nullable = false)
    private Double minSafetyStock;

    @Column(name = "route", length = 50)
    private String route;

    @Builder.Default
    @Column(name = "is_controlled", nullable = false)
    private Boolean isControlled = false;

    @Builder.Default
    @Column(name = "requires_prescription", nullable = false)
    private Boolean requiresPrescription = false;

    @Column(name = "withdrawal_hours")
    private Integer withdrawalHours;

    @Column(name = "dose_reference", length = 255)
    private String doseReference;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "last_restocked_at", nullable = false)
    private LocalDateTime lastRestockedAt;

    public double getAvailableStock() {
        double onHand = currentStock != null ? currentStock : 0;
        double held = heldStock != null ? heldStock : 0;
        return onHand - held;
    }
}
