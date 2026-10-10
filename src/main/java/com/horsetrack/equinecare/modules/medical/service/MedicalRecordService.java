package com.horsetrack.equinecare.modules.medical.service;
import com.horsetrack.equinecare.modules.medical.dto.MedicalRecordCreateRequestDTO;

public interface MedicalRecordService {
    Integer createMedicalRecord(MedicalRecordCreateRequestDTO request);
}
