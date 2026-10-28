package com.horsetrack.equinecare.modules.horse.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.horse.dto.*;
import com.horsetrack.equinecare.modules.horse.service.HorseService;
import com.horsetrack.equinecare.modules.training.dto.AvailableHorseDTO;
import com.horsetrack.equinecare.modules.training.service.TrainingPlanService;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "Đăng ký ngựa", description = "Chủ ngựa đăng ký cho chính mình. Club Manager đăng ký hộ thì phải chọn ownerId.")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> registerHorse(@Valid @RequestBody HorseRegistrationRequestDTO request) {
        HorseResponseDTO response = horseService.registerHorse(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Horse registered successfully", response));
    }

    @GetMapping("/my-horses/{ownerId}")
    @PreAuthorize("hasAnyAuthority('Horse Owner', 'Club Manager')")
    @Operation(summary = "Ngựa của một chủ", description = "Chủ ngựa chỉ xem danh sách của chính mình.")
    public ResponseEntity<ApiResponse<List<HorseResponseDTO>>> getMyHorses(@PathVariable Integer ownerId) {
        List<HorseResponseDTO> response = horseService.getMyHorses(ownerId);
        return ResponseEntity.ok(ApiResponse.ok("Fetched my horses successfully", response));
    }

    @GetMapping("/available-for-training")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Club Manager')")
    @Operation(summary = "Ngựa có thể lập giáo án", description = "HLV chỉ thấy ngựa được Club Manager phân công cho mình.")
    public ResponseEntity<ApiResponse<List<AvailableHorseDTO>>> getAvailableHorsesForTraining() {
        List<AvailableHorseDTO> response = trainingPlanService.getAvailableHorses();
        return ResponseEntity.ok(ApiResponse.ok("Fetched available horses successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('Club Manager', 'Trainer', 'Veterinarian', 'Groom')")
    @Operation(summary = "Danh sách ngựa", description = "Phân trang page, size. status: PENDING, APPROVED, REJECTED. HLV chỉ thấy ngựa được phân công.")
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
    @Operation(summary = "Chi tiết ngựa")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> getHorseDetail(@PathVariable Integer id) {
        HorseResponseDTO response = horseService.getHorseDetail(id);
        return ResponseEntity.ok(ApiResponse.ok("Fetched horse details successfully", response));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Duyệt hồ sơ và cấp chuồng", description = "Club Manager duyệt hồ sơ đăng ký ngựa và gán chuồng.")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> approveHorse(
            @PathVariable Integer id,
            @Valid @RequestBody HorseApprovalRequestDTO request) {
        HorseResponseDTO response = horseService.approveHorse(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Đã duyệt hồ sơ và cấp chuồng cho ngựa", response));
    }

    // Club Manager từ chối hồ sơ (có thể cho phép nộp lại)
    // Club Manager phân công HLV phụ trách ngựa; giáo án đang chạy được bàn giao cho HLV mới
    @PutMapping("/{id}/assign-trainer")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Phân công HLV", description = "Gửi yêu cầu xác nhận. Ngựa chưa đổi người phụ trách cho đến khi HLV xác nhận. Giáo án đang chạy được bàn giao cho HLV mới.")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> assignTrainer(
            @PathVariable Integer id,
            @Valid @RequestBody com.horsetrack.equinecare.modules.horse.dto.HorseAssignTrainerRequestDTO request) {
        HorseResponseDTO response = horseService.assignTrainer(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi yêu cầu xác nhận tới HLV. Ngựa chưa đổi người phụ trách cho đến khi HLV xác nhận.", response));
    }

    // Club Manager bỏ phân công HLV (không cho phép khi ngựa còn giáo án chưa kết thúc)
    @DeleteMapping("/{id}/assign-trainer")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Bỏ phân công HLV", description = "Không bỏ được khi ngựa còn giáo án chưa kết thúc. HLV hiện tại vẫn phụ trách cho đến khi xác nhận.")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> unassignTrainer(@PathVariable Integer id) {
        HorseResponseDTO response = horseService.unassignTrainer(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi yêu cầu bỏ phân công. HLV hiện tại vẫn phụ trách cho đến khi xác nhận.", response));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Từ chối hồ sơ ngựa", description = "Club Manager từ chối hồ sơ. Có thể cho phép chủ ngựa nộp lại.")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> rejectHorse(
            @PathVariable Integer id,
            @Valid @RequestBody HorseRejectRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã từ chối hồ sơ ngựa", horseService.rejectHorse(id, request)));
    }

    // Chủ ngựa sửa thông tin và nộp lại hồ sơ đã bị từ chối (khi được cho phép)
    @PutMapping("/{id}/resubmit")
    @PreAuthorize("hasAuthority('Horse Owner')")
    @Operation(summary = "Nộp lại hồ sơ ngựa", description = "Chủ ngựa sửa thông tin và nộp lại hồ sơ đã bị từ chối khi được cho phép.")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> resubmitHorse(
            @PathVariable Integer id,
            @Valid @RequestBody HorseResubmitRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã nộp lại hồ sơ, chờ Quản lý CLB duyệt", horseService.resubmitHorse(id, request)));
    }

    // Lịch sử hồ sơ (mỗi lần nộp / quyết định), mới nhất trước
    @GetMapping("/{id}/registrations")
    @PreAuthorize("hasAnyAuthority('Club Manager', 'Horse Owner')")
    @Operation(summary = "Lịch sử hồ sơ ngựa", description = "Mỗi lần nộp hoặc quyết định duyệt / từ chối, mới nhất trước.")
    public ResponseEntity<ApiResponse<List<RegistrationHistoryDTO>>> getRegistrationHistory(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy lịch sử hồ sơ thành công", horseService.getRegistrationHistory(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Club Manager', 'Trainer', 'Veterinarian')")
    @Operation(summary = "Cập nhật hồ sơ ngựa", description = "Sửa cân nặng. Đổi healthStatus hoặc isLocked chỉ dành cho bác sĩ.")
    public ResponseEntity<ApiResponse<HorseResponseDTO>> updateHorse(
            @PathVariable Integer id,
            @Valid @RequestBody HorseUpdateRequestDTO request) {
        HorseResponseDTO response = horseService.updateHorse(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Horse updated successfully", response));
    }
}
