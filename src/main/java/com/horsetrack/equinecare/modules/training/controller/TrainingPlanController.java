package com.horsetrack.equinecare.modules.training.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.training.dto.DashboardStatsDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingPlanSummaryDTO;
import com.horsetrack.equinecare.modules.training.service.TrainingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.horsetrack.equinecare.modules.training.dto.StaffAssignDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingPlanDetailDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingSessionCreateRequestDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainingPlanCreateRequestDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/training-plans")
@RequiredArgsConstructor
public class TrainingPlanController {

    private final TrainingPlanService trainingPlanService;

    @GetMapping("/dashboard-stats")
    public ResponseEntity<ApiResponse<DashboardStatsDTO>> getDashboardStats() {
        DashboardStatsDTO stats = trainingPlanService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.ok("Fetched dashboard stats successfully", stats));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TrainingPlanSummaryDTO>>> getTrainingPlans(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<TrainingPlanSummaryDTO> plansPage = trainingPlanService.getTrainingPlans(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Fetched training plans successfully", plansPage));
    }

    // Trainer tạo giáo án mới cho ngựa (sau đó thêm buổi tập qua POST /{planId}/sessions)
    @PostMapping
    @PreAuthorize("hasAuthority('Trainer')")
    public ResponseEntity<ApiResponse<Integer>> createTrainingPlan(
            @Valid @RequestBody TrainingPlanCreateRequestDTO request) {
        Integer planId = trainingPlanService.createTrainingPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo giáo án huấn luyện thành công", planId));
    }

    // ==========================================
    // API CHO MÀN HÌNH LẬP LỊCH & CALENDAR
    // ==========================================

    // 1. Lấy chi tiết Giáo án & Các buổi tập để đổ lên Calendar
    @GetMapping("/{planId}")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Club Manager')")
    public ResponseEntity<ApiResponse<TrainingPlanDetailDTO>> getPlanDetail(@PathVariable Integer planId) {
        TrainingPlanDetailDTO detail = trainingPlanService.getTrainingPlanDetail(planId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy dữ liệu lịch tập thành công", detail));
    }

    // 2. Lấy danh sách Groom/Trainer đổ vào Dropdown gán việc
    @GetMapping("/assignable-staff")
    @PreAuthorize("hasAuthority('Trainer')")
    public ResponseEntity<ApiResponse<List<StaffAssignDTO>>> getAssignableStaff(@RequestParam Integer planId) {
        // Chỉ Groom đang hoạt động cùng khu chuồng với ngựa của giáo án (SRS: khu → ngựa → Groom của khu)
        List<StaffAssignDTO> staffList = trainingPlanService.getAssignableStaff(planId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách nhân sự phân công thành công", staffList));
    }

    // 3. Trainer tạo buổi tập trên Lịch
    @PostMapping("/{planId}/sessions")
    @PreAuthorize("hasAuthority('Trainer')")
    public ResponseEntity<ApiResponse<Void>> addSessionToPlan(
            @PathVariable Integer planId,
            @Valid @RequestBody TrainingSessionCreateRequestDTO request) {

        trainingPlanService.addTrainingSessionToPlan(planId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã thêm buổi tập vào lịch thành công!", null));
    }
}

