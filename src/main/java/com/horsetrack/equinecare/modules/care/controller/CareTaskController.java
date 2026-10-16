package com.horsetrack.equinecare.modules.care.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.care.dto.CareTaskDTO;
import com.horsetrack.equinecare.modules.care.service.CareTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/care-tasks")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Groom')")
public class CareTaskController {

    private final CareTaskService careTaskService;

    @GetMapping("/my-tasks")
    public ResponseEntity<ApiResponse<List<CareTaskDTO>>> getMyTasks() {
        List<CareTaskDTO> tasks = careTaskService.getMyTasksToday();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách công việc thành công", tasks));
    }

    @PutMapping("/{taskId}/complete")
    public ResponseEntity<ApiResponse<Void>> completeTask(@PathVariable Integer taskId) {
        careTaskService.completeTask(taskId);
        return ResponseEntity.ok(ApiResponse.ok("Đã hoàn thành công việc!", null));
    }
}