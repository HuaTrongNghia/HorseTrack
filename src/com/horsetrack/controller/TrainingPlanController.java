package com.horsetrack.controller;

import com.horsetrack.dto.TrainingPlanCreateRequestDTO;
import com.horsetrack.entity.TrainingSession;
import com.horsetrack.repository.TrainingSessionRepository; // Thêm import repository
import com.horsetrack.response.ApiResponse;
import com.horsetrack.service.TrainingPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List; // Thêm import List

@RestController
@RequestMapping("/api/v1/training-plans")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Hỗ trợ frontend gọi API
public class TrainingPlanController {

    private final TrainingPlanService trainingPlanService;
    private final TrainingSessionRepository trainingSessionRepository; // Khai báo repository để lấy danh sách buổi tập

    @PostMapping
    public ResponseEntity<ApiResponse<Integer>> createTrainingPlan(
            @Valid @RequestBody TrainingPlanCreateRequestDTO request) {

        try {
            Integer planId = trainingPlanService.createPlanWithSessions(request);

            ApiResponse<Integer> response = new ApiResponse<>(
                    201,
                    "Tạo giáo án và phân công lịch tập thành công!",
                    planId
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>(400, e.getMessage(), null)
            );
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<TrainingSession>> getAllSessions() {
        List<TrainingSession> sessions = trainingSessionRepository.findAll();
        return ResponseEntity.ok(sessions);
    }
}