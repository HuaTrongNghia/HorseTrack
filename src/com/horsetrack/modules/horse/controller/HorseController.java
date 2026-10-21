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
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyAuthority('Horse Owner', 'Club Manager')")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> registerHorse(@Valid @RequestBody HorseRegistrationRequestDTO request) {
        HorseResponseDTO response = horseService.registerHorse(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Horse registered successfully", response));
    }

    @GetMapping("/my-horses/{ownerId}")
    @PreAuthorize("hasAnyAuthority('Horse Owner', 'Club Manager')")
    public ResponseEntity<ApiResponse<List<HorseResponseDTO>>> getMyHorses(@PathVariable Integer ownerId) {
        List<HorseResponseDTO> response = horseService.getMyHorses(ownerId);
        return ResponseEntity.ok(ApiResponse.ok("Fetched my horses successfully", response));
    }

    @GetMapping("/available-for-training")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Club Manager')")
    public ResponseEntity<ApiResponse<List<AvailableHorseDTO>>> getAvailableHorsesForTraining() {
        List<AvailableHorseDTO> response = trainingPlanService.getAvailableHorses();
        return ResponseEntity.ok(ApiResponse.ok("Fetched available horses successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('Club Manager', 'Trainer', 'Veterinarian', 'Groom')")
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
    @PreAuthorize("hasAnyAuthority('Club Manager', 'Trainer', 'Veterinarian', 'Groom', 'Horse Owner')")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> getHorseDetail(@PathVariable Integer id) {
        HorseResponseDTO response = horseService.getHorseDetail(id);
        return ResponseEntity.ok(ApiResponse.ok("Fetched horse details successfully", response));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('Club Manager')")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> approveHorse(
            @PathVariable Integer id,
            @Valid @RequestBody HorseApprovalRequestDTO request) {
        HorseResponseDTO response = horseService.approveHorse(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Đã duyệt hồ sơ và cấp chuồng cho ngựa", response));
    }

    // Club Manager từ chối hồ sơ (lý do tối thiểu 10 ký tự, có thể cho phép nộp lại)
    // Club Manager phân công HLV phụ trách ngựa; giáo án đang chạy được bàn giao cho HLV mới
    @PutMapping("/{id}/assign-trainer")
    @PreAuthorize("hasAuthority('Club Manager')")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> assignTrainer(
            @PathVariable Integer id,
            @Valid @RequestBody com.horsetrack.equinecare.modules.horse.dto.HorseAssignTrainerRequestDTO request) {
        HorseResponseDTO response = horseService.assignTrainer(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Đã phân công HLV " + response.getAssignedTrainerName() + " phụ trách ngựa " + response.getHorseName(), response));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('Club Manager')")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> rejectHorse(
            @PathVariable Integer id,
            @Valid @RequestBody HorseRejectRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã từ chối hồ sơ ngựa", horseService.rejectHorse(id, request)));
    }

    // Chủ ngựa sửa thông tin và nộp lại hồ sơ đã bị từ chối (khi được cho phép)
    @PutMapping("/{id}/resubmit")
    @PreAuthorize("hasAuthority('Horse Owner')")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> resubmitHorse(
            @PathVariable Integer id,
            @Valid @RequestBody HorseResubmitRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã nộp lại hồ sơ, chờ Quản lý CLB duyệt", horseService.resubmitHorse(id, request)));
    }

    // Lịch sử hồ sơ (mỗi lần nộp / quyết định), mới nhất trước
    @GetMapping("/{id}/registrations")
    @PreAuthorize("hasAnyAuthority('Club Manager', 'Horse Owner')")
    public ResponseEntity<ApiResponse<List<RegistrationHistoryDTO>>> getRegistrationHistory(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy lịch sử hồ sơ thành công", horseService.getRegistrationHistory(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Club Manager', 'Trainer', 'Veterinarian')")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> updateHorse(
            @PathVariable Integer id,
            @Valid @RequestBody HorseUpdateRequestDTO request) {
        HorseResponseDTO response = horseService.updateHorse(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Horse updated successfully", response));
    }
}
