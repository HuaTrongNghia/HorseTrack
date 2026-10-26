package com.horsetrack.equinecare.modules.assignment.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.assignment.dto.AssignmentRejectRequestDTO;
import com.horsetrack.equinecare.modules.assignment.dto.AssignmentRequestResponseDTO;
import com.horsetrack.equinecare.modules.assignment.service.AssignmentDecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assignments")
@RequiredArgsConstructor
public class AssignmentRequestController {

    private final AssignmentDecisionService decisionService;

    @GetMapping("/mine")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Groom')")
    public ResponseEntity<ApiResponse<List<AssignmentRequestResponseDTO>>> mine() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy phân công chờ xác nhận thành công", decisionService.mine()));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('Club Manager')")
    public ResponseEntity<ApiResponse<List<AssignmentRequestResponseDTO>>> pending() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy phân công đang chờ thành công", decisionService.pending()));
    }

    @PutMapping("/{id}/accept")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Groom')")
    public ResponseEntity<ApiResponse<Void>> accept(@PathVariable Integer id) {
        decisionService.accept(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã xác nhận phân công", null));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Groom')")
    public ResponseEntity<ApiResponse<Void>> reject(@PathVariable Integer id, @Valid @RequestBody AssignmentRejectRequestDTO request) {
        decisionService.reject(id, request.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Đã từ chối phân công", null));
    }
}
