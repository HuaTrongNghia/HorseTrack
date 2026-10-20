package com.horsetrack.equinecare.modules.inventory.entity;
import jakarta.persistence.*;
import lombok.*;

/** Nhà cung cấp (bảng Suppliers). Tên nhà cung cấp là duy nhất (UQ_Suppliers_name). */
@Entity
@Table(name = "Suppliers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_id")
    private Integer supplierId;

    @Column(name = "supplier_name", nullable = false, length = 150)
    private String supplierName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    @Column(name = "address", length = 255)
    private String address;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
