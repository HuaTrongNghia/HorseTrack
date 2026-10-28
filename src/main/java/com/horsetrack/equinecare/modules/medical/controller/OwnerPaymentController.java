package com.horsetrack.equinecare.modules.medical.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.medical.service.MedicalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/owner/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Horse Owner')")
public class OwnerPaymentController {

    private final MedicalRecordService medicalRecordService;

    @PostMapping("/medical-records/{recordId}/vnpay")
    @Operation(summary = "Thanh toán bệnh án qua VNPay", description = "Chủ ngựa thanh toán bệnh án đã được duyệt. Ngựa được đưa vào điều trị.")
    public ResponseEntity<ApiResponse<Void>> payWithVNPay(@PathVariable Integer recordId) {
        medicalRecordService.processPayment(recordId, "VNPAY");
        return ResponseEntity.ok(ApiResponse.ok("Thanh toán VNPay thành công. Ngựa bắt đầu được đưa vào điều trị.", null));
    }
}
