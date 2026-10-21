package com.horsetrack.equinecare.modules.medical.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.medical.dto.PreRaceExamDTO;
import com.horsetrack.equinecare.modules.medical.dto.SubmitExamResultDTO;
import com.horsetrack.equinecare.modules.medical.service.PreRaceExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/prerace-exams", "/api/prerace-exams"})
@CrossOrigin("*")
@RequiredArgsConstructor
public class PreRaceExamController {

    private final PreRaceExamService examService;

    // Lấy dữ liệu cho bảng. Frontend gọi: GET /api/prerace-exams?tab=CHO_KHAM
    @GetMapping
    public ResponseEntity<ApiResponse<List<PreRaceExamDTO>>> getExams(@RequestParam(defaultValue = "CHO_KHAM") String tab) {
        List<PreRaceExamDTO> data = examService.getExamsByTab(tab);
        return ResponseEntity.ok(ApiResponse.ok("Thành công", data));
    }

    // Ghi nhận kết quả khám. Frontend gọi: POST /api/prerace-exams/DX-0009/examine
    @PostMapping("/{proposalCode}/examine")
    public ResponseEntity<ApiResponse<String>> submitResult(
            @PathVariable String proposalCode,
            @RequestBody SubmitExamResultDTO dto) {
        try {
            examService.submitExamResult(proposalCode, dto);
            return ResponseEntity.ok(ApiResponse.ok("Đã lưu kết quả khám cho " + proposalCode, null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
