package com.horsetrack.equinecare.modules.inventory.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name = "Inventory_Items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer itemId;
    private String itemName;
    private Integer currentStock;
}
