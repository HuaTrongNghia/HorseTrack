package com.horsetrack.equinecare.modules.training.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.training.dto.SessionEvaluateRequestDTO;
import com.horsetrack.equinecare.modules.training.dto.SessionExecuteRequestDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainerScheduleDTO;
import com.horsetrack.equinecare.modules.training.service.TrainingExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/training-sessions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('Trainer', 'Groom', 'Club Manager')")
public class TrainingExecutionController {

    private final TrainingExecutionService trainingExecutionService;

    @GetMapping("/my-schedule")
    public ResponseEntity<ApiResponse<List<TrainerScheduleDTO>>> getMySchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<TrainerScheduleDTO> schedule = trainingExecutionService.getMySchedule(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok("Fetched my schedule successfully", schedule));
    }

    // Groom xác nhận nhận buổi tập; giáo án chuyển sang ACTIVE khi đã có buổi được xác nhận và tới ngày bắt đầu
    @PutMapping("/{sessionId}/confirm")
    @PreAuthorize("hasAuthority('Groom')")
    public ResponseEntity<ApiResponse<String>> confirmSession(@PathVariable Integer sessionId) {
        String planStatus = trainingExecutionService.confirmSession(sessionId);
        String message = "ACTIVE".equalsIgnoreCase(planStatus)
                ? "Đã xác nhận buổi tập. Giáo án đang chạy."
                : "Đã xác nhận buổi tập. Giáo án sẽ chạy khi tới ngày bắt đầu.";
        return ResponseEntity.ok(ApiResponse.ok(message, planStatus));
    }

    @PutMapping("/{sessionId}/execute")
    public ResponseEntity<ApiResponse<Void>> executeSession(
            @PathVariable Integer sessionId,
            @Valid @RequestBody SessionExecuteRequestDTO request) {
        trainingExecutionService.executeSession(sessionId, request);
        return ResponseEntity.ok(ApiResponse.ok("Session executed successfully", null));
    }

    // HLV hoặc Quản lý CLB đánh giá phong độ sau buổi tập
    @PutMapping("/{sessionId}/evaluate")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Club Manager')")
    public ResponseEntity<ApiResponse<Void>> evaluateSession(
            @PathVariable Integer sessionId,
            @Valid @RequestBody SessionEvaluateRequestDTO request) {
        trainingExecutionService.evaluateSession(sessionId, request);
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh giá buổi tập thành công", null));
    }
}
