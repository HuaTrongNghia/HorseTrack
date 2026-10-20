package com.horsetrack.equinecare.modules.medical.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.medical.dto.MedicalCaseDTO;
import com.horsetrack.equinecare.modules.medical.dto.VetActionRequestDTO;
import com.horsetrack.equinecare.modules.medical.dto.VetLockRequestDTO;
import com.horsetrack.equinecare.modules.medical.service.MedicalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/medical", "/api/medical"})
@CrossOrigin("*")
@RequiredArgsConstructor
public class MedicalController {

    private final MedicalService medicalService;

    // API 1: Nút kích hoạt VetLock khẩn cấp (nhân viên trực tiếp chăm sóc/huấn luyện được bấm khi phát hiện chấn thương)
    @PostMapping("/vet-lock")
    @PreAuthorize("hasAnyAuthority('Veterinarian', 'Trainer', 'Groom')")
    public ResponseEntity<ApiResponse<String>> applyVetLock(@RequestBody VetLockRequestDTO dto) {
        try {
            medicalService.applyVetLock(dto);
            return ResponseEntity.ok(ApiResponse.ok("Đã khóa huấn luyện (VetLock) thành công!", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // API 2: Nút mở khóa khi ngựa đã khỏe lại (chỉ Bác sĩ thú y được xác nhận ngựa đủ điều kiện tập lại)
    @PostMapping("/release-lock/{horseId}")
    @PreAuthorize("hasAuthority('Veterinarian')")
    public ResponseEntity<ApiResponse<String>> releaseVetLock(@PathVariable Integer horseId) {
        try {
            medicalService.releaseVetLock(horseId);
            return ResponseEntity.ok(ApiResponse.ok("Đã mở khóa, ngựa có thể tập luyện trở lại.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // API 3: Lấy danh sách ca khám (Hộp thư ca khám)
    @GetMapping("/cases")
    @PreAuthorize("hasAuthority('Veterinarian')")
    public ResponseEntity<ApiResponse<List<MedicalCaseDTO>>> getInboxCases(@RequestParam(required = false, defaultValue = "ALL") String status) {
        try {
            List<MedicalCaseDTO> cases = medicalService.getInboxCases(status);
            return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách ca khám thành công", cases));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // API 4: Bác sĩ xử lý ca khám (khám, khóa tập, chỉ định)
    @PostMapping("/cases/action")
    @PreAuthorize("hasAuthority('Veterinarian')")
    public ResponseEntity<ApiResponse<String>> processVetAction(@Valid @RequestBody VetActionRequestDTO dto,
                                                                 @RequestParam(required = false, defaultValue = "1") Integer vetId) {
        try {
            medicalService.processVetAction(dto, vetId);
            return ResponseEntity.ok(ApiResponse.ok("Xử lý ca khám thành công", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
