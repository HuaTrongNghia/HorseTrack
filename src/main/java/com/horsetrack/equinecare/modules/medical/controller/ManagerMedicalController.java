package com.horsetrack.equinecare.modules.medical.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.medical.service.MedicalRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/manager/medical-records")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Club Manager')")
public class ManagerMedicalController {

    private final MedicalRecordService medicalRecordService;

    @PutMapping("/{recordId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveMedicalRecord(@PathVariable Integer recordId) {
        medicalRecordService.approveMedicalRecord(recordId);
        return ResponseEntity.ok(ApiResponse.ok("Đã phê duyệt phác đồ điều trị. Chờ chủ ngựa thanh toán.", null));
    }
}