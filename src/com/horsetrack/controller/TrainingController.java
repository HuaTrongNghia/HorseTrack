package com.horsetrack.controller;

import com.horsetrack.dto.TrainingPlanDTO;
import com.horsetrack.dto.TrainingMetricDTO;
import com.horsetrack.dto.EvaluationDTO;
import com.horsetrack.response.ApiResponse;
import com.horsetrack.service.TrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/training")
public class TrainingController {

    @Autowired
    private TrainingService trainingService;

    // API Lập giáo án
    @PostMapping("/plan")
    public ResponseEntity<ApiResponse> createPlan(@RequestBody TrainingPlanDTO request) {
        ApiResponse response = trainingService.createPlanAndCheckVetlock(request);
        if (response.getStatus() == 400) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    // API Gửi chỉ số tập
    @PostMapping("/metrics")
    public ResponseEntity<ApiResponse> submitMetrics(@RequestBody TrainingMetricDTO request) {
        ApiResponse response = trainingService.processMetrics(request);
        return ResponseEntity.ok(response);
    }

    // API Đánh giá
    @PostMapping("/evaluate")
    public ResponseEntity<ApiResponse> evaluateSession(@RequestBody EvaluationDTO request) {
        ApiResponse response = trainingService.submitEvaluation(request);
        return ResponseEntity.ok(response);
    }
}