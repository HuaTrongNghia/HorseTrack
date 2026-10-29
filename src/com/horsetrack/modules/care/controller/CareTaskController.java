package com.horsetrack.equinecare.modules.care.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.care.dto.CareTaskCreateRequestDTO;
import com.horsetrack.equinecare.modules.care.dto.CareTaskDTO;
import com.horsetrack.equinecare.modules.care.service.CareTaskService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/care-tasks")
@RequiredArgsConstructor
public class CareTaskController {

    private final CareTaskService careTaskService;

    // Trainer giao việc chăm sóc cho Groom
    @PostMapping
    @PreAuthorize("hasAuthority('Trainer')")
    @Operation(summary = "Giao việc chăm sóc", description = "HLV giao việc cho Groom đã xác nhận tài khoản và phụ trách khu của ngựa.")
    public ResponseEntity<ApiResponse<CareTaskDTO>> createTask(@Valid @RequestBody CareTaskCreateRequestDTO request) {
        CareTaskDTO task = careTaskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã giao việc chăm sóc cho Groom thành công", task));
    }

    // Club Manager xem các việc chăm sóc bị cảnh báo trễ quá 45 phút mà chưa xong (BR_05)
    @GetMapping("/overdue")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Việc chăm sóc trễ hạn", description = "Mặc định (scope=open): việc quá 45 phút sau giờ hẹn mà chưa xong (BR_05). "
            + "scope=today (hoặc date=yyyy-MM-dd): mọi việc trễ SLA của ngày đó, kể cả đã hoàn thành trễ - dùng cho thống kê.")
    public ResponseEntity<ApiResponse<List<CareTaskDTO>>> getOverdueTasks(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date) {
        boolean byDay = date != null || "today".equalsIgnoreCase(scope);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách việc trễ hạn thành công",
                byDay ? careTaskService.getLateTasksOn(date) : careTaskService.getOverdueTasks()));
    }

    // HLV theo dõi việc đã giao cho Groom (ngựa mình phụ trách), Club Manager xem mọi ngựa: trạng thái, giờ hoàn thành
    @GetMapping("/assigned")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Club Manager')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Việc chăm sóc đã giao", description = "HLV: ngựa mình phụ trách; Club Manager: mọi ngựa. date mặc định hôm nay (yyyy-MM-dd), status TODO / IN_PROGRESS / DONE / SKIPPED.")
    public ResponseEntity<com.horsetrack.equinecare.common.base.ApiResponse<java.util.List<com.horsetrack.equinecare.modules.care.dto.CareTaskDTO>>> assigned(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(com.horsetrack.equinecare.common.base.ApiResponse.ok("Lấy danh sách việc đã giao thành công", careTaskService.getAssignedTasks(date, status)));
    }

    @GetMapping("/my-tasks")
    @PreAuthorize("hasAuthority('Groom')")
    @Operation(summary = "Việc chăm sóc của tôi", description = "Mặc định hôm nay; date=yyyy-MM-dd để xem trước ngày khác (ví dụ việc cho ăn ngày mai).")
    public ResponseEntity<ApiResponse<List<CareTaskDTO>>> getMyTasks(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date) {
        List<CareTaskDTO> tasks = careTaskService.getMyTasks(date);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách công việc thành công", tasks));
    }

    @PutMapping("/{taskId}/complete")
    @PreAuthorize("hasAuthority('Groom')")
    @Operation(summary = "Hoàn thành việc chăm sóc")
    public ResponseEntity<ApiResponse<Void>> completeTask(@PathVariable Integer taskId) {
        careTaskService.completeTask(taskId);
        return ResponseEntity.ok(ApiResponse.ok("Đã hoàn thành công việc!", null));
    }
}
