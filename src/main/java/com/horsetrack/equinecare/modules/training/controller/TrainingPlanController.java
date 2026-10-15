package com.horsetrack.equinecare.modules.training.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.training.dto.DashboardStatsDTO;
import com.horsetrack.equinecare.modules.training.dto.StaffAssignDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingPlanSummaryDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingSessionCreateRequestDTO;
import com.horsetrack.equinecare.modules.training.service.TrainingPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/training-plans")
@RequiredArgsConstructor
public class TrainingPlanController {

    private final TrainingPlanService trainingPlanService;

    // ... Các code cũ (getDashboardStats, getTrainingPlans) giữ nguyên ở đây ...

    // 1. Lấy danh sách Groom/Trainer đổ vào Dropdown gán việc
    @GetMapping("/assignable-staff")
    @PreAuthorize("hasAuthority('Head Trainer')")
    public ResponseEntity<ApiResponse<List<StaffAssignDTO>>> getAssignableStaff() {
        List<StaffAssignDTO> staffList = trainingPlanService.getAssignableStaff();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách nhân sự phân công thành công", staffList));
    }

    // 2. Head Trainer bấm tạo buổi tập trên Lịch
    @PostMapping("/{planId}/sessions")
    @PreAuthorize("hasAuthority('Head Trainer')")
    public ResponseEntity<ApiResponse<Void>> addSessionToPlan(
            @PathVariable Integer planId,
            @Valid @RequestBody TrainingSessionCreateRequestDTO request) {

        trainingPlanService.addTrainingSessionToPlan(planId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã thêm buổi tập vào lịch thành công!", null));
    }
}