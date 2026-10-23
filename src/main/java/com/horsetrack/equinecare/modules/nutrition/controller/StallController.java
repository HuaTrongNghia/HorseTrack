package com.horsetrack.equinecare.modules.nutrition.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.nutrition.dto.StallResponseDTO;
import com.horsetrack.equinecare.modules.nutrition.service.StallService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stalls")
@RequiredArgsConstructor
public class StallController {

    private final StallService stallService;

    /**
     * API 1: Lấy danh sách sơ đồ chuồng trại (Stall Map)
     * Quyền: Bất kỳ Role nhân viên nào đã đăng nhập.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StallResponseDTO>>> getStallMap() {
        List<StallResponseDTO> response = stallService.getStallMap();
        return ResponseEntity.ok(ApiResponse.ok("Lấy sơ đồ chuồng trại thành công", response));
    }

    // Groom (chuồng trong khu mình) hoặc Club Manager đổi trạng thái chuồng: CLEAN / NEEDS_CLEANING / MAINTENANCE
    @PutMapping("/{id}/clean-status")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('Groom', 'Club Manager')")
    public ResponseEntity<ApiResponse<StallResponseDTO>> updateCleanStatus(@PathVariable Integer id,
            @jakarta.validation.Valid @RequestBody com.horsetrack.equinecare.modules.nutrition.dto.StallCleanStatusDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật trạng thái chuồng", stallService.updateCleanStatus(id, request)));
    }
}