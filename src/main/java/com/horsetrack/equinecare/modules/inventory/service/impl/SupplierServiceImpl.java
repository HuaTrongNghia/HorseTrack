package com.horsetrack.equinecare.modules.inventory.service.impl;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.inventory.dto.SupplierDTO;
import com.horsetrack.equinecare.modules.inventory.dto.SupplierRequestDTO;
import com.horsetrack.equinecare.modules.inventory.entity.Supplier;
import com.horsetrack.equinecare.modules.inventory.repository.SupplierRepository;
import com.horsetrack.equinecare.modules.inventory.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SupplierDTO> getSuppliers(boolean activeOnly) {
        List<Supplier> suppliers = activeOnly
                ? supplierRepository.findByIsActiveTrueOrderBySupplierNameAsc()
                : supplierRepository.findAllByOrderBySupplierNameAsc();
        return suppliers.stream().map(SupplierServiceImpl::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierDTO createSupplier(SupplierRequestDTO request) {
        String name = request.getSupplierName().trim();
        if (supplierRepository.existsBySupplierNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Nhà cung cấp '" + name + "' đã tồn tại.");
        }
        Supplier supplier = Supplier.builder().isActive(true).build();
        applyInfo(supplier, request);
        return toDTO(supplierRepository.save(supplier));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierDTO updateSupplier(Integer supplierId, SupplierRequestDTO request) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + supplierId));
        String name = request.getSupplierName().trim();
        if (supplierRepository.existsBySupplierNameIgnoreCaseAndSupplierIdNot(name, supplierId)) {
            throw new IllegalArgumentException("Nhà cung cấp '" + name + "' đã tồn tại.");
        }
        applyInfo(supplier, request);
        if (request.getIsActive() != null) {
            supplier.setIsActive(request.getIsActive());
        }
        return toDTO(supplierRepository.save(supplier));
    }

    private void applyInfo(Supplier supplier, SupplierRequestDTO request) {
        supplier.setSupplierName(request.getSupplierName().trim());
        supplier.setContactPhone(request.getContactPhone());
        supplier.setContactEmail(request.getContactEmail());
        supplier.setAddress(request.getAddress());
    }

    static SupplierDTO toDTO(Supplier s) {
        return SupplierDTO.builder()
                .supplierId(s.getSupplierId())
                .supplierName(s.getSupplierName())
                .contactPhone(s.getContactPhone())
                .contactEmail(s.getContactEmail())
                .address(s.getAddress())
                .isActive(s.getIsActive())
                .build();
    }
}
