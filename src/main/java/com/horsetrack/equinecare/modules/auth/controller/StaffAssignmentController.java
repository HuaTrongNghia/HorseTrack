package com.horsetrack.equinecare.modules.auth.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.auth.dto.GroomZoneRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.GroomZoneResponseDTO;
import com.horsetrack.equinecare.modules.auth.service.StaffAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Phân công khu chăm sóc cho Groom (Club Manager) */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Club Manager')")
public class StaffAssignmentController {

    private final StaffAssignmentService staffAssignmentService;

    @GetMapping("/{id}/barn-zone")
    public ResponseEntity<ApiResponse<GroomZoneResponseDTO>> getGroomZone(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy khu chăm sóc thành công", staffAssignmentService.getGroomZone(id)));
    }

    @PutMapping("/{id}/barn-zone")
    @Operation(summary = "Phân công / đổi / bỏ khu chăm sóc của Groom",
            description = "barnZone là tên khu đang có (ví dụ \"Khu B\"); gửi null hoặc rỗng để bỏ phân công. "
                    + "Không cho đổi / bỏ khi Groom còn việc chăm sóc hoặc buổi tập chưa thực hiện ở khu hiện tại.")
    public ResponseEntity<ApiResponse<GroomZoneResponseDTO>> updateGroomZone(@PathVariable Integer id,
                                                                            @Valid @RequestBody GroomZoneRequestDTO request) {
        GroomZoneResponseDTO dto = staffAssignmentService.updateGroomZone(id, request.getBarnZone());
        String message = dto.getBarnZone() == null
                ? "Đã bỏ phân công khu của " + dto.getFullName()
                : "Đã phân công " + dto.getFullName() + " phụ trách " + dto.getBarnZone();
        return ResponseEntity.ok(ApiResponse.ok(message, dto));
    }
}
