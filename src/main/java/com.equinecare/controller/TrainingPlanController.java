package com.equinecare.controller;

import com.equinecare.dto.request.TrainingPlanCreateRequest;
import com.equinecare.dto.request.TrainingDayCreateRequest;
import com.equinecare.dto.response.ApiResponse;
import com.equinecare.entity.User;
import com.equinecare.entity.TrainingPlan;
import com.equinecare.entity.TrainingDay;
import com.equinecare.service.TrainingPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/training")
@CrossOrigin("*")
public class TrainingPlanController {

    @Autowired
    private TrainingPlanService trainingPlanService;

    // Lấy danh sách Grooms cho màn hình Lịch (Calendar View)
    @GetMapping("/grooms")
    public ResponseEntity<ApiResponse<List<User>>> getGroomList() {
        List<User> grooms = trainingPlanService.getActiveGrooms();
        return ResponseEntity.ok(new ApiResponse<>(true, "Lấy danh sách Groom thành công", grooms));
    }

    // BƯỚC 1: HLV Lập thông tin Giáo án cơ bản
    @PostMapping("/plans")
    public ResponseEntity<ApiResponse<?>> createPlan(@RequestBody TrainingPlanCreateRequest request) {
        try {
            TrainingPlan newPlan = trainingPlanService.executeTrainingPlanFlow(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(true, "Tạo giáo án thành công. Hãy chuyển sang bước lập lịch.", newPlan));

        } catch (RuntimeException e) {
            if ("VETLOCK_ACTIVE".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse<>(false, "Ngựa đang bị khóa y tế. Đã gửi thông báo về cho HLV và Chủ ngựa.", null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    // BƯỚC 2: HLV Bấm vào Calendar để gán buổi tập & phân công Groom
    @PostMapping("/plans/{planId}/days")
    public ResponseEntity<ApiResponse<?>> addSessionToCalendar(
            @PathVariable Integer planId,
            @RequestBody TrainingDayCreateRequest request) {
        try {
            TrainingDay newDay = trainingPlanService.addSessionToPlan(planId, request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(true, "Đã thêm buổi tập vào lịch và phân công Groom.", newDay));

        } catch (RuntimeException e) {
            if ("VETLOCK_ACTIVE".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse<>(false, "Không thể gán lịch. Ngựa đang bị khóa y tế khẩn cấp!", null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    // API: Groom thao tác xác nhận nhiệm vụ (Flow 2.1 kết thúc tại đây)
    @PutMapping("/days/{trainingDayId}/confirm")
    public ResponseEntity<ApiResponse<?>> confirmTaskByGroom(@PathVariable Integer trainingDayId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Groom đã xác nhận lịch phân công thành công", null));
    }
}