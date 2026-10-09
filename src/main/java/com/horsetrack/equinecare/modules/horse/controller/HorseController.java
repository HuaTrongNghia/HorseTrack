package com.horsetrack.equinecare.modules.horse.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.horse.dto.*;
import com.horsetrack.equinecare.modules.horse.service.HorseService;
import com.horsetrack.equinecare.modules.training.dto.AvailableHorseDTO;
import com.horsetrack.equinecare.modules.training.service.TrainingPlanService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/horses")
public class HorseController {

    private final HorseService horseService;
    private final TrainingPlanService trainingPlanService;

    public HorseController(HorseService horseService, TrainingPlanService trainingPlanService) {
        this.horseService = horseService;
        this.trainingPlanService = trainingPlanService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> registerHorse(@Valid @RequestBody HorseRegistrationRequestDTO request) {
        HorseResponseDTO response = horseService.registerHorse(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Horse registered successfully", response));
    }

    @GetMapping("/my-horses/{ownerId}")
    public ResponseEntity<ApiResponse<List<HorseResponseDTO>>> getMyHorses(@PathVariable Integer ownerId) {
        List<HorseResponseDTO> response = horseService.getMyHorses(ownerId);
        return ResponseEntity.ok(ApiResponse.ok("Fetched my horses successfully", response));
    }

    @GetMapping("/available-for-training")
    public ResponseEntity<ApiResponse<List<AvailableHorseDTO>>> getAvailableHorsesForTraining() {
        List<AvailableHorseDTO> response = trainingPlanService.getAvailableHorses();
        return ResponseEntity.ok(ApiResponse.ok("Fetched available horses successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<HorsePageResponseDTO>> getAllHorses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        Page<HorseResponseDTO> horsePage = horseService.getAllHorses(page, size, status);
        
        HorsePageResponseDTO response = HorsePageResponseDTO.builder()
                .horses(horsePage.getContent())
                .currentPage(horsePage.getNumber())
                .totalPages(horsePage.getTotalPages())
                .totalElements(horsePage.getTotalElements())
                .build();
                
        return ResponseEntity.ok(ApiResponse.ok("Fetched all horses successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> getHorseDetail(@PathVariable Integer id) {
        HorseResponseDTO response = horseService.getHorseDetail(id);
        return ResponseEntity.ok(ApiResponse.ok("Fetched horse details successfully", response));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> approveHorse(
            @PathVariable Integer id,
            @Valid @RequestBody HorseApprovalRequestDTO request) {
        HorseResponseDTO response = horseService.approveHorse(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Horse approved and assigned to stall successfully", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> updateHorse(
            @PathVariable Integer id,
            @Valid @RequestBody HorseUpdateRequestDTO request) {
        HorseResponseDTO response = horseService.updateHorse(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Horse updated successfully", response));
    }
}