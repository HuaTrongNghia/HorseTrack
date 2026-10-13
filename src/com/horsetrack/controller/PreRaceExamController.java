package com.horsetrack.controller;

import com.horsetrack.dto.PreRaceExamDTO;
import com.horsetrack.dto.SubmitExamResultDTO;
import com.horsetrack.response.ApiResponse;
import com.horsetrack.service.PreRaceExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prerace-exams")
@CrossOrigin("*")
@RequiredArgsConstructor
public class PreRaceExamController {

    private final PreRaceExamService examService;

    // Lấy dữ liệu cho bảng. Frontend gọi: GET /api/prerace-exams?tab=CHO_KHAM
    @GetMapping
    public ResponseEntity<ApiResponse<List<PreRaceExamDTO>>> getExams(@RequestParam(defaultValue = "CHO_KHAM") String tab) {
        List<PreRaceExamDTO> data = examService.getExamsByTab(tab);
        return ResponseEntity.ok(new ApiResponse<>(200, "Thành công", data));
    }

    // Ghi nhận kết quả khám. Frontend gọi: POST /api/prerace-exams/DX-0009/examine
    @PostMapping("/{proposalCode}/examine")
    public ResponseEntity<ApiResponse<String>> submitResult(
            @PathVariable String proposalCode,
            @RequestBody SubmitExamResultDTO dto) {
        try {
            examService.submitExamResult(proposalCode, dto);
            return ResponseEntity.ok(new ApiResponse<>(200, "Đã lưu kết quả khám cho " + proposalCode, null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }
}