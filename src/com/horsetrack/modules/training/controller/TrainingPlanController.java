package com.horsetrack.equinecare.modules.training.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.training.dto.OwnerHorseListDTO;
import com.horsetrack.equinecare.modules.training.dto.OwnerTrainingProgressDTO;
import com.horsetrack.equinecare.modules.training.service.OwnerTrainingService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/owner/training")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Horse Owner')")
public class OwnerTrainingController {

    private final OwnerTrainingService ownerTrainingService;

    @GetMapping("/my-horses")
    @Operation(summary = "Ngựa của tôi", description = "Chủ ngựa xem ngựa của mình: tên, trạng thái duyệt hồ sơ và chuồng.")
    public ResponseEntity<ApiResponse<List<OwnerHorseListDTO>>> getMyHorses() {
        List<OwnerHorseListDTO> myHorses = ownerTrainingService.getMyHorses();
        return ResponseEntity.ok(ApiResponse.ok("Fetched my horses successfully", myHorses));
    }

    @GetMapping("/horses/{horseId}/progress")
    @Operation(summary = "Tiến độ tập của ngựa", description = "Chủ ngựa chỉ xem ngựa của mình.")
    public ResponseEntity<ApiResponse<OwnerTrainingProgressDTO>> getTrainingProgress(@PathVariable Integer horseId) {
        OwnerTrainingProgressDTO progress = ownerTrainingService.getTrainingProgress(horseId);
        return ResponseEntity.ok(ApiResponse.ok("Fetched training progress successfully", progress));
    }
}
