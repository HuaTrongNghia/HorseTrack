package com.horsetrack.equinecare.modules.medical.service.impl;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryItemRepository;
import com.horsetrack.equinecare.modules.inventory.repository.PurchaseOrderRepository;
import com.horsetrack.equinecare.modules.medical.dto.MedicalRecordCreateRequestDTO;
import com.horsetrack.equinecare.modules.medical.dto.PrescriptionItemCreateDTO;
import com.horsetrack.equinecare.modules.medical.entity.InjuryMark;
import com.horsetrack.equinecare.modules.medical.entity.MedicalRecord;
import com.horsetrack.equinecare.modules.medical.entity.PrescriptionItem;
import com.horsetrack.equinecare.modules.medical.exception.InvalidPrescriptionException;
import com.horsetrack.equinecare.modules.medical.repository.InjuryMarkRepository;
import com.horsetrack.equinecare.modules.medical.repository.MedicalRecordRepository;
import com.horsetrack.equinecare.modules.medical.repository.PrescriptionItemRepository;
import com.horsetrack.equinecare.modules.medical.service.MedicalRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicalRecordServiceImpl implements MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final InjuryMarkRepository injuryMarkRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer createMedicalRecord(MedicalRecordCreateRequestDTO request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User vet = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Veterinarian not found"));

        Horse horse = horseRepository.findById(request.getHorseId())
                .orElseThrow(() -> new RuntimeException("Horse not found"));

        if (request.getPrescriptionItems() != null) {
            for (PrescriptionItemCreateDTO itemDto : request.getPrescriptionItems()) {
                InventoryItem invItem = inventoryItemRepository.findById(itemDto.getItemId())
                        .orElseThrow(() -> new RuntimeException("Inventory item not found for ID: " + itemDto.getItemId()));
                
                int currentStock = invItem.getCurrentStock() != null ? invItem.getCurrentStock() : 0;
                Integer incomingStock = purchaseOrderRepository.getIncomingQuantity(itemDto.getItemId(), request.getVisitDate());
                if (incomingStock == null) incomingStock = 0;
                
                int totalAvailable = currentStock + incomingStock;
                
                if (totalAvailable < itemDto.getQuantityPrescribed()) {
                    throw new InvalidPrescriptionException(String.format(
                            "Vật tư [%s] không đủ đáp ứng. Khả dụng (bao gồm lô hàng sắp về): [%d], Yêu cầu: [%d]. Vui lòng điều chỉnh phác đồ!",
                            invItem.getItemName(), totalAvailable, itemDto.getQuantityPrescribed()));
                }
            }
        }

        if (Boolean.TRUE.equals(request.getIsLockoutOrdered())) {
            horse.setIsLocked(true);
            horse.setHealthStatus("VetLock = ACTIVE");
            horseRepository.save(horse);
        }

        MedicalRecord record = MedicalRecord.builder()
                .horse(horse)
                .veterinarian(vet)
                .diagnosis(request.getDiagnosis())
                .symptoms(request.getSymptoms())
                .isLockoutOrdered(request.getIsLockoutOrdered())
                .status("PENDING_APPROVAL")
                .createdAt(LocalDateTime.now())
                .build();
        
        MedicalRecord savedRecord = medicalRecordRepository.save(record);

        if (request.getInjuryMarks() != null) {
            List<InjuryMark> injuryMarks = request.getInjuryMarks().stream().map(dto -> 
                InjuryMark.builder()
                    .affectedBodyPart(dto.getBodyPartCode())
                    .coordX(dto.getCoordX())
                    .coordY(dto.getCoordY())
                    .severityLevel(dto.getSeverityLevel())
                    .medicalRecord(savedRecord)
                    .build()
            ).collect(Collectors.toList());
            injuryMarkRepository.saveAll(injuryMarks);
        }

        if (request.getPrescriptionItems() != null) {
            List<PrescriptionItem> prescriptionItems = request.getPrescriptionItems().stream().map(dto -> {
                InventoryItem invItem = inventoryItemRepository.findById(dto.getItemId()).orElseThrow();
                return PrescriptionItem.builder()
                    .medicineName(invItem.getItemName())
                    .dosageInstructions(dto.getDosage())
                    .quantity(BigDecimal.valueOf(dto.getQuantityPrescribed()))
                    .medicalRecord(savedRecord)
                    .build();
            }).collect(Collectors.toList());
            prescriptionItemRepository.saveAll(prescriptionItems);
        }

        return savedRecord.getRecordId();
    }
}
