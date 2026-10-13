package com.horsetrack.controller;

import com.horsetrack.dto.TrainingPlanCreateRequestDTO;
import com.horsetrack.repository.TrainingPlanRepository; // Đã đổi import cho đúng
import com.horsetrack.response.ApiResponse;
import com.horsetrack.service.TrainingPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/training-plans")
@RequiredArgsConstructor
@CrossOrigin("*") // Hỗ trợ frontend gọi API
public class TrainingPlanController {

    private final TrainingPlanService trainingPlanService;

    // ĐÃ SỬA: Đổi từ Session sang Plan để lấy đúng danh sách Giáo án
    private final TrainingPlanRepository trainingPlanRepository;

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
    public ResponseEntity<?> getAllPlans() {
        // Trả về toàn bộ danh sách từ bảng Training_Plans
        return ResponseEntity.ok(trainingPlanRepository.findAll());
    }
}