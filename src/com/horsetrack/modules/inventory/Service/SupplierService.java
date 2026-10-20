package com.horsetrack.equinecare.modules.inventory.service;

import com.horsetrack.equinecare.modules.inventory.dto.SupplierDTO;
import com.horsetrack.equinecare.modules.inventory.dto.SupplierRequestDTO;

import java.util.List;

public interface SupplierService {
    List<SupplierDTO> getSuppliers(boolean activeOnly);
    SupplierDTO createSupplier(SupplierRequestDTO request);
    SupplierDTO updateSupplier(Integer supplierId, SupplierRequestDTO request);
}
