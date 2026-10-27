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
    private final com.horsetrack.equinecare.modules.training.service.TrainingLockHandler trainingLockHandler;
    private final com.horsetrack.equinecare.modules.audit.service.AuditTrail auditTrail;
    private final com.horsetrack.equinecare.modules.medical.service.HorseConditionNotifier conditionNotifier;

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
                
                double currentStock = invItem.getCurrentStock() != null ? invItem.getCurrentStock() : 0;
                Double incomingStock = purchaseOrderRepository.getIncomingQuantity(itemDto.getItemId(), request.getVisitDate());
                if (incomingStock == null) incomingStock = 0.0;

                double totalAvailable = currentStock + incomingStock;

                if (totalAvailable < itemDto.getQuantityPrescribed()) {
                    throw new InvalidPrescriptionException(String.format(
                            "Vật tư [%s] không đủ đáp ứng. Khả dụng (bao gồm lô hàng sắp về): [%s], Yêu cầu: [%d]. Vui lòng điều chỉnh phác đồ!",
                            invItem.getItemName(), BigDecimal.valueOf(totalAvailable).stripTrailingZeros().toPlainString(),
                            itemDto.getQuantityPrescribed()));
                }
            }
        }

        if (Boolean.TRUE.equals(request.getIsLockoutOrdered())) {
            // Bác sĩ chỉ định khóa tập => kết luận chấn thương (giá trị hợp lệ theo CHK_Horse_Health)
            horse.setIsLocked(true);
            horse.setHealthStatus("INJURED");
            horseRepository.save(horse);
            int cancelled = trainingLockHandler.onHorseLocked(horse, "Chỉ định khóa tập trong bệnh án: " + request.getDiagnosis());
            auditTrail.record("VET_LOCK", "Horses", "isLocked=false", "isLocked=true, health=INJURED | ngựa " + horse.getHorseName()
                    + " (horse_id=" + horse.getHorseId() + ") | hủy " + cancelled + " buổi tập | Bệnh án: " + request.getDiagnosis());
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
                    .horse(savedRecord.getHorse())
                    .createdAt(LocalDateTime.now())
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

        String extra = Boolean.TRUE.equals(request.getIsLockoutOrdered()) ? "Bác sĩ đã khóa huấn luyện." : null;
        if (request.getInjuryMarks() != null && !request.getInjuryMarks().isEmpty()) {
            String parts = request.getInjuryMarks().stream()
                    .map(m -> m.getBodyPartCode() + (m.getSeverityLevel() == null ? "" : " (" + m.getSeverityLevel() + ")"))
                    .collect(Collectors.joining(", "));
            extra = (extra == null ? "" : extra + "\n") + "Vị trí chấn thương: " + parts;
        }
        conditionNotifier.onIllness(horse, request.getDiagnosis(), request.getSymptoms(), extra);

        return savedRecord.getRecordId();
    }

    // ==========================================
    // LUỒNG PHÊ DUYỆT VÀ THANH TOÁN (VNPay)
    // ==========================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveMedicalRecord(Integer recordId) {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bệnh án"));

        if (!"PENDING_APPROVAL".equals(record.getStatus())) {
            throw new RuntimeException("Hồ sơ không ở trạng thái chờ duyệt");
        }

        // Ngựa cấp cứu không chờ chủ ngựa thanh toán rồi mới điều trị
        if (record.getHorse() != null && "EMERGENCY".equalsIgnoreCase(record.getHorse().getHealthStatus())) {
            record.setStatus("IN_TREATMENT");
        } else {
            record.setStatus("APPROVED_AWAITING_PAYMENT");
        }
        medicalRecordRepository.save(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processPayment(Integer recordId, String paymentMethod) {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bệnh án"));

        if (!"APPROVED_AWAITING_PAYMENT".equals(record.getStatus())) {
            throw new RuntimeException("Hồ sơ không ở trạng thái chờ thanh toán");
        }

        // Xác thực đúng chủ ngựa đang đăng nhập mới được thanh toán
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        if (record.getHorse().getOwner() != null && !record.getHorse().getOwner().getUserId().equals(user.getUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không phải chủ của ngựa này");
        }

        // Giả lập (Mock) gọi cổng VNPay thành công
        record.setStatus("IN_TREATMENT");
        medicalRecordRepository.save(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmRecovery(Integer recordId) {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bệnh án"));

        // Bác sĩ xác nhận hoàn tất điều trị
        record.setStatus("COMPLETED");
        medicalRecordRepository.save(record);

        // Mở khóa VetLock cho ngựa
        Horse horse = record.getHorse();
        if (Boolean.TRUE.equals(record.getIsLockoutOrdered()) && horse != null) {
            horse.setIsLocked(false);
            horse.setHealthStatus("HEALTHY");
            horseRepository.save(horse);
            trainingLockHandler.onHorseUnlocked(horse);
        }
    }
}
