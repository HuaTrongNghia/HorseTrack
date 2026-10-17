package com.horsetrack.equinecare.modules.training.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.training.dto.OwnerHorseListDTO;
import com.horsetrack.equinecare.modules.training.dto.OwnerTrainingProgressDTO;
import com.horsetrack.equinecare.modules.training.service.OwnerTrainingService;
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
    public ResponseEntity<ApiResponse<List<OwnerHorseListDTO>>> getMyHorses() {
        List<OwnerHorseListDTO> myHorses = ownerTrainingService.getMyHorses();
        return ResponseEntity.ok(ApiResponse.ok("Fetched my horses successfully", myHorses));
    }

    @GetMapping("/horses/{horseId}/progress")
    public ResponseEntity<ApiResponse<OwnerTrainingProgressDTO>> getTrainingProgress(@PathVariable Integer horseId) {
        OwnerTrainingProgressDTO progress = ownerTrainingService.getTrainingProgress(horseId);
        return ResponseEntity.ok(ApiResponse.ok("Fetched training progress successfully", progress));
    }
}
