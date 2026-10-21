package com.horsetrack.equinecare.modules.nutrition.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.nutrition.dto.DietChangeDecisionDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.DietChangeRequestCreateDTO;
import com.horsetrack.equinecare.modules.nutrition.dto.DietChangeResponseDTO;
import com.horsetrack.equinecare.modules.nutrition.service.DietChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Diet Change Requests", description = "Mã phu đề xuất thức ăn đặc biệt, Bác sĩ thú y xét duyệt")
public class DietChangeController {

    private final DietChangeService dietChangeService;

    @PostMapping("/diet-requests")
    @PreAuthorize("hasAnyAuthority('Groom', 'Trainer')")
    @Operation(summary = "Mã phu hoặc HLV gửi đề xuất thức ăn đặc biệt cho ngựa")
    public ResponseEntity<ApiResponse<DietChangeResponseDTO>> createDietRequest(
            @Valid @RequestBody DietChangeRequestCreateDTO request) {
        DietChangeResponseDTO response = dietChangeService.createDietRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã gửi đề xuất thức ăn đặc biệt thành công", response));
    }

    @GetMapping("/diet-requests")
    @PreAuthorize("hasAnyAuthority('Veterinarian', 'Groom', 'Trainer', 'Club Manager')")
    @Operation(summary = "Xem danh sách đề xuất thức ăn đặc biệt")
    public ResponseEntity<ApiResponse<List<DietChangeResponseDTO>>> getDietRequests(
            @RequestParam(required = false) Integer horseId,
            @RequestParam(required = false) String status) {
        List<DietChangeResponseDTO> response = dietChangeService.getDietRequests(horseId, status);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách đề xuất thức ăn đặc biệt thành công", response));
    }

    @GetMapping("/diet-requests/{requestId}")
    @PreAuthorize("hasAnyAuthority('Veterinarian', 'Groom', 'Trainer', 'Club Manager')")
    @Operation(summary = "Xem chi tiết đề xuất thức ăn đặc biệt")
    public ResponseEntity<ApiResponse<DietChangeResponseDTO>> getDietRequestDetail(
            @PathVariable Integer requestId) {
        DietChangeResponseDTO response = dietChangeService.getDietRequestDetail(requestId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết đề xuất thức ăn đặc biệt thành công", response));
    }

    @PutMapping("/diet-requests/{requestId}/decide")
    @PreAuthorize("hasAnyAuthority('Veterinarian', 'Club Manager')")
    @Operation(summary = "Bác sĩ thú y duyệt hoặc từ chối đề xuất thức ăn đặc biệt")
    public ResponseEntity<ApiResponse<DietChangeResponseDTO>> decideDietRequest(
            @PathVariable Integer requestId,
            @Valid @RequestBody DietChangeDecisionDTO decision) {
        DietChangeResponseDTO response = dietChangeService.decideDietRequest(requestId, decision);
        String msg = Boolean.TRUE.equals(decision.getApprove())
                ? "Đã duyệt đề xuất thức ăn đặc biệt thành công"
                : "Đã từ chối đề xuất thức ăn đặc biệt";
        return ResponseEntity.ok(ApiResponse.ok(msg, response));
    }
}
