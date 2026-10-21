package com.horsetrack.equinecare.modules.medical.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.medical.dto.MedicalRecordCreateRequestDTO;
import com.horsetrack.equinecare.modules.medical.exception.InvalidPrescriptionException;
import com.horsetrack.equinecare.modules.medical.service.MedicalRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/veterinary/medical-records")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Veterinarian')")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    @PostMapping
    public ResponseEntity<ApiResponse<Integer>> createMedicalRecord(@Valid @RequestBody MedicalRecordCreateRequestDTO request) {
        Integer recordId = medicalRecordService.createMedicalRecord(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Medical record created successfully", recordId));
    }

    @ExceptionHandler(InvalidPrescriptionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPrescriptionException(InvalidPrescriptionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }
}
