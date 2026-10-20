package com.horsetrack.equinecare.modules.care.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.care.dto.CareTaskCreateRequestDTO;
import com.horsetrack.equinecare.modules.care.dto.CareTaskDTO;
import com.horsetrack.equinecare.modules.care.service.CareTaskService;
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
    public ResponseEntity<ApiResponse<CareTaskDTO>> createTask(@Valid @RequestBody CareTaskCreateRequestDTO request) {
        CareTaskDTO task = careTaskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã giao việc chăm sóc cho Groom thành công", task));
    }

    @GetMapping("/my-tasks")
    @PreAuthorize("hasAuthority('Groom')")
    public ResponseEntity<ApiResponse<List<CareTaskDTO>>> getMyTasks() {
        List<CareTaskDTO> tasks = careTaskService.getMyTasksToday();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách công việc thành công", tasks));
    }

    @PutMapping("/{taskId}/complete")
    @PreAuthorize("hasAuthority('Groom')")
    public ResponseEntity<ApiResponse<Void>> completeTask(@PathVariable Integer taskId) {
        careTaskService.completeTask(taskId);
        return ResponseEntity.ok(ApiResponse.ok("Đã hoàn thành công việc!", null));
    }
}
