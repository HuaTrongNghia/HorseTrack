package com.horsetrack.equinecare.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDTO {
    private Integer supplierId;
    private String supplierName;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private Boolean isActive;
}
