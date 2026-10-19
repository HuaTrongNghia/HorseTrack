package com.horsetrack.controller;

import com.horsetrack.dto.TreatmentProtocolCreateDTO;
import com.horsetrack.entity.Medication;
import com.horsetrack.entity.TreatmentProtocol;
import com.horsetrack.response.ApiResponse;
import com.horsetrack.service.MedicalManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medical-management")
@CrossOrigin("*")
@RequiredArgsConstructor
public class MedicalManagementController {

    private final MedicalManagementService service;

    @GetMapping("/medications")
    public ResponseEntity<ApiResponse<List<Medication>>> getMedications() {
        try {
            List<Medication> list = service.getAllMedications();
            return ResponseEntity.ok(new ApiResponse<>(200, "Lấy danh mục thuốc thành công", list));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }

    @PostMapping("/protocols")
    public ResponseEntity<ApiResponse<TreatmentProtocol>> createProtocol(@RequestBody TreatmentProtocolCreateDTO dto) {
        try {
            TreatmentProtocol protocol = service.createProtocol(dto);
            return ResponseEntity.ok(new ApiResponse<>(200, "Khởi tạo phác đồ điều trị thành công!", protocol));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }
}
