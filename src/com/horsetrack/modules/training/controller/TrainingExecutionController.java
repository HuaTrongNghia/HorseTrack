package com.horsetrack.equinecare.modules.training.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
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
@PreAuthorize("hasAnyAuthority('Trainer', 'Groom')")
public class TrainingExecutionController {

    private final TrainingExecutionService trainingExecutionService;

    @GetMapping("/my-schedule")
    public ResponseEntity<ApiResponse<List<TrainerScheduleDTO>>> getMySchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<TrainerScheduleDTO> schedule = trainingExecutionService.getMySchedule(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok("Fetched my schedule successfully", schedule));
    }

    @PutMapping("/{sessionId}/execute")
    public ResponseEntity<ApiResponse<Void>> executeSession(
            @PathVariable Integer sessionId,
            @Valid @RequestBody SessionExecuteRequestDTO request) {
        trainingExecutionService.executeSession(sessionId, request);
        return ResponseEntity.ok(ApiResponse.ok("Session executed successfully", null));
    }
}
