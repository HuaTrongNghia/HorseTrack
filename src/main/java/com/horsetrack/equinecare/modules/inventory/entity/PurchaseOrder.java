package com.horsetrack.equinecare.modules.inventory.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
@Entity
@Table(name = "Purchase_Orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer orderId;
    private Integer itemId;
    private Integer quantity;
    private LocalDate expectedDeliveryDate;
    private String status;
}
