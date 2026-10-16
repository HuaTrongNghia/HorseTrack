package com.horsetrack.controller;

import com.horsetrack.dto.VetLockRequestDTO;
import com.horsetrack.response.ApiResponse;
import com.horsetrack.service.MedicalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medical")
@CrossOrigin("*")
@RequiredArgsConstructor
public class MedicalController {

    private final MedicalService medicalService;

    // API 1: Nút kích hoạt VetLock khẩn cấp
    @PostMapping("/vet-lock")
    public ResponseEntity<ApiResponse<String>> applyVetLock(@RequestBody VetLockRequestDTO dto) {
        try {
            medicalService.applyVetLock(dto);
            return ResponseEntity.ok(new ApiResponse<>(200, "Đã khóa huấn luyện (VetLock) thành công!", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }

    // API 2: Nút mở khóa khi ngựa đã khỏe lại
    @PostMapping("/release-lock/{horseId}")
    public ResponseEntity<ApiResponse<String>> releaseVetLock(@PathVariable Integer horseId) {
        try {
            medicalService.releaseVetLock(horseId);
            return ResponseEntity.ok(new ApiResponse<>(200, "Đã mở khóa, ngựa có thể tập luyện trở lại.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }

}