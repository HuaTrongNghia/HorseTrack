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
    private final com.horsetrack.equinecare.modules.training.service.SessionMonitorService sessionMonitorService;

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

    // F22-02: Groom bắt đầu buổi tập đã xác nhận, đúng ngày tập (CONFIRMED -> IN_PROGRESS)
    @PutMapping("/{sessionId}/start")
    @PreAuthorize("hasAuthority('Groom')")
    public ResponseEntity<ApiResponse<com.horsetrack.equinecare.modules.training.dto.TrainingSessionDTO>> startSession(@PathVariable Integer sessionId) {
        return ResponseEntity.ok(ApiResponse.ok("Đã bắt đầu buổi tập", sessionMonitorService.start(sessionId)));
    }

    // Groom nhập chỉ số; vượt ngưỡng thì buổi tập tự tạm dừng (PAUSED_PENDING_TRAINER) và báo HLV
    @PostMapping("/{sessionId}/readings")
    @PreAuthorize("hasAuthority('Groom')")
    public ResponseEntity<ApiResponse<com.horsetrack.equinecare.modules.training.dto.TrainingSessionDTO>> addReading(
            @PathVariable Integer sessionId,
            @Valid @RequestBody com.horsetrack.equinecare.modules.training.dto.SessionReadingRequestDTO request) {
        var dto = sessionMonitorService.addReading(sessionId, request);
        String message = "PAUSED_PENDING_TRAINER".equals(dto.getStatus())
                ? "Chỉ số vượt ngưỡng: buổi tập đã tạm dừng và báo HLV. " + dto.getPausedReason()
                : "Đã ghi nhận chỉ số";
        return ResponseEntity.ok(ApiResponse.ok(message, dto));
    }

    // Groom nhập nhịp tim hồi phục khi buổi tập đang tạm dừng
    @PostMapping("/{sessionId}/recovery")
    @PreAuthorize("hasAuthority('Groom')")
    public ResponseEntity<ApiResponse<com.horsetrack.equinecare.modules.training.dto.TrainingSessionDTO>> addRecovery(
            @PathVariable Integer sessionId,
            @Valid @RequestBody com.horsetrack.equinecare.modules.training.dto.SessionRecoveryRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã ghi nhận nhịp tim hồi phục", sessionMonitorService.addRecovery(sessionId, request)));
    }

    // HLV điều chỉnh và cho buổi tập tạm dừng tập tiếp (nhịp hồi phục phải dưới ngưỡng, ngựa không bị khóa)
    @PutMapping("/{sessionId}/resume")
    @PreAuthorize("hasAuthority('Trainer')")
    public ResponseEntity<ApiResponse<com.horsetrack.equinecare.modules.training.dto.TrainingSessionDTO>> resumeSession(
            @PathVariable Integer sessionId,
            @Valid @RequestBody com.horsetrack.equinecare.modules.training.dto.SessionResumeRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Buổi tập tiếp tục", sessionMonitorService.resume(sessionId, request)));
    }

    // Dừng buổi tập đang tạm dừng vì chỉ số vượt ngưỡng: mặc định gửi ca cho bác sĩ, buổi tập chờ bác sĩ quyết định (WAITING_VET)
    @PutMapping("/{sessionId}/stop")
    public ResponseEntity<ApiResponse<String>> stopSession(@PathVariable Integer sessionId,
                                                           @Valid @RequestBody com.horsetrack.equinecare.modules.training.dto.SessionStopRequestDTO request) {
        String status = trainingExecutionService.stopSession(sessionId, request);
        String message = "WAITING_VET".equals(status)
                ? "Đã dừng buổi tập và gửi ca cho bác sĩ. Buổi tập sẽ kết thúc khi bác sĩ khóa huấn luyện hoặc đóng ca."
                : "Đã dừng và kết thúc buổi tập.";
        return ResponseEntity.ok(ApiResponse.ok(message, status));
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
