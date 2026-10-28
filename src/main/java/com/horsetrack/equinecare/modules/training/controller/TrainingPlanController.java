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
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/training-plans")
@RequiredArgsConstructor
public class TrainingPlanController {

    private final TrainingPlanService trainingPlanService;
    private final com.horsetrack.equinecare.modules.training.service.TrainingPlanLifecycle planLifecycle;

    // Trainer chỉ nhận số liệu và giáo án của các ngựa được phân công
    @GetMapping("/dashboard-stats")
    @PreAuthorize("hasAnyAuthority('Club Manager', 'Trainer', 'Groom', 'Veterinarian')")
    @Operation(summary = "Số liệu bảng điều khiển giáo án", description = "HLV chỉ nhận số liệu của ngựa được phân công.")
    public ResponseEntity<ApiResponse<DashboardStatsDTO>> getDashboardStats() {
        DashboardStatsDTO stats = trainingPlanService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.ok("Fetched dashboard stats successfully", stats));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('Club Manager', 'Trainer', 'Groom', 'Veterinarian')")
    @Operation(summary = "Danh sách giáo án", description = "Lọc theo status. Phân trang page, size. HLV chỉ thấy ngựa được phân công.")
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
    @Operation(summary = "Lập giáo án huấn luyện", description = "Tạo giáo án cho ngựa được phân công. Thêm buổi tập bằng POST /{planId}/sessions.")
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
    @Operation(summary = "Chi tiết giáo án và lịch tập")
    public ResponseEntity<ApiResponse<TrainingPlanDetailDTO>> getPlanDetail(@PathVariable Integer planId) {
        TrainingPlanDetailDTO detail = trainingPlanService.getTrainingPlanDetail(planId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy dữ liệu lịch tập thành công", detail));
    }

    // 2. Lấy danh sách Groom/Trainer đổ vào Dropdown gán việc
    @GetMapping("/assignable-staff")
    @PreAuthorize("hasAuthority('Trainer')")
    @Operation(summary = "Nhân sự có thể phân công", description = "Groom đang hoạt động cùng khu chuồng với ngựa của giáo án.")
    public ResponseEntity<ApiResponse<List<StaffAssignDTO>>> getAssignableStaff(@RequestParam Integer planId) {
        // Chỉ Groom đang hoạt động cùng khu chuồng với ngựa của giáo án (SRS: khu → ngựa → Groom của khu)
        List<StaffAssignDTO> staffList = trainingPlanService.getAssignableStaff(planId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách nhân sự phân công thành công", staffList));
    }

    // 3. Trainer tạo buổi tập trên Lịch
    @PostMapping("/{planId}/sessions")
    @PreAuthorize("hasAuthority('Trainer')")
    @Operation(summary = "Thêm buổi tập vào giáo án", description = "HLV đặt buổi trên lịch và gán Groom cùng khu.")
    public ResponseEntity<ApiResponse<Void>> addSessionToPlan(
            @PathVariable Integer planId,
            @Valid @RequestBody TrainingSessionCreateRequestDTO request) {

        trainingPlanService.addTrainingSessionToPlan(planId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã thêm buổi tập vào lịch thành công!", null));
    }

    // Hủy giáo án đang chờ / đang chạy / tạm dừng: các buổi chưa thực hiện bị hủy, ngựa lập được giáo án mới
    @PutMapping("/{planId}/cancel")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Club Manager')")
    @Operation(summary = "Hủy giáo án", description = "Giáo án đang chờ, đang chạy hoặc tạm dừng. Các buổi chưa thực hiện bị hủy. Bắt buộc lý do. Ngựa lập được giáo án mới.")
    public ResponseEntity<ApiResponse<String>> cancelPlan(@PathVariable Integer planId,
                                                          @Valid @RequestBody com.horsetrack.equinecare.modules.training.dto.PlanCancelRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã hủy giáo án", planLifecycle.cancel(planId, request.getReason())));
    }

    // Cho giáo án tạm dừng chạy lại (ngựa không bị khóa, không còn buổi tập chờ bác sĩ)
    @PutMapping("/{planId}/resume")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Club Manager')")
    @Operation(summary = "Cho giáo án chạy lại", description = "Ngựa không bị khóa và không còn buổi tập chờ bác sĩ.")
    public ResponseEntity<ApiResponse<String>> resumePlan(@PathVariable Integer planId) {
        String status = planLifecycle.resume(planId);
        return ResponseEntity.ok(ApiResponse.ok("ACTIVE".equals(status)
                ? "Giáo án đang chạy lại." : "Giáo án chuyển về chờ: chạy khi Groom xác nhận buổi tập và tới ngày bắt đầu.", status));
    }
}

