package com.horsetrack.equinecare.modules.medical.service;
import com.horsetrack.equinecare.modules.medical.dto.MedicalRecordCreateRequestDTO;

public interface MedicalRecordService {
    Integer createMedicalRecord(MedicalRecordCreateRequestDTO request);

    // THÊM 3 HÀM MỚI NÀY
    void approveMedicalRecord(Integer recordId);
    void processPayment(Integer recordId, String paymentMethod);
    void confirmRecovery(Integer recordId);
}