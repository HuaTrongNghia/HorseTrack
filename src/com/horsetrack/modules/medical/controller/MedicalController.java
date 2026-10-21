package com.horsetrack.equinecare.modules.medical.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.medical.dto.CaseCloseRequestDTO;
import com.horsetrack.equinecare.modules.medical.dto.CaseReportRequestDTO;
import com.horsetrack.equinecare.modules.medical.dto.MedicalCaseDTO;
import com.horsetrack.equinecare.modules.medical.dto.VetActionRequestDTO;
import com.horsetrack.equinecare.modules.medical.dto.VetLockRequestDTO;
import com.horsetrack.equinecare.modules.medical.service.MedicalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/medical", "/api/medical"})
@CrossOrigin("*")
@RequiredArgsConstructor
public class MedicalController {

    private final MedicalService medicalService;

    // API 1: Nút kích hoạt VetLock khẩn cấp (nhân viên trực tiếp chăm sóc/huấn luyện được bấm khi phát hiện chấn thương)
    @PostMapping("/vet-lock")
    @PreAuthorize("hasAnyAuthority('Veterinarian', 'Trainer', 'Groom')")
    public ResponseEntity<ApiResponse<String>> applyVetLock(@RequestBody VetLockRequestDTO dto) {
        try {
            medicalService.applyVetLock(dto);
            return ResponseEntity.ok(ApiResponse.ok("Đã khóa huấn luyện (VetLock) thành công!", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // API 2: Nút mở khóa khi ngựa đã khỏe lại (chỉ Bác sĩ thú y được xác nhận ngựa đủ điều kiện tập lại)
    @PostMapping("/release-lock/{horseId}")
    @PreAuthorize("hasAuthority('Veterinarian')")
    public ResponseEntity<ApiResponse<String>> releaseVetLock(@PathVariable Integer horseId) {
        try {
            medicalService.releaseVetLock(horseId);
            return ResponseEntity.ok(ApiResponse.ok("Đã mở khóa, ngựa có thể tập luyện trở lại.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // API 3: Hộp thư ca khám. Bác sĩ xem mọi ca; Groom / Trainer chỉ xem ca do chính mình báo
    @GetMapping("/cases")
    @PreAuthorize("hasAnyAuthority('Veterinarian', 'Groom', 'Trainer')")
    public ResponseEntity<ApiResponse<List<MedicalCaseDTO>>> getInboxCases(@RequestParam(required = false, defaultValue = "ALL") String status) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách ca khám thành công", medicalService.getInboxCases(status)));
    }

    // API 4: Groom / Trainer báo sự cố sức khỏe của ngựa -> ca mới (NEW) trong hộp thư của bác sĩ
    @PostMapping("/cases")
    @PreAuthorize("hasAnyAuthority('Groom', 'Trainer')")
    public ResponseEntity<ApiResponse<MedicalCaseDTO>> reportCase(@Valid @RequestBody CaseReportRequestDTO dto) {
        MedicalCaseDTO created = medicalService.reportCase(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã gửi ca " + created.getCaseId() + " cho Bác sĩ thú y", created));
    }

    // API 5: Bác sĩ ghi nhận kết quả khám và hướng xử lý (ca chuyển sang MONITORING).
    // Tham số vetId cũ vẫn được chấp nhận nhưng bỏ qua: bác sĩ là người đang đăng nhập.
    @PostMapping("/cases/action")
    @PreAuthorize("hasAuthority('Veterinarian')")
    public ResponseEntity<ApiResponse<MedicalCaseDTO>> processVetAction(@Valid @RequestBody VetActionRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok("Xử lý ca khám thành công", medicalService.processVetAction(dto)));
    }

    // API 6: Bác sĩ đóng ca khi không cần điều trị thêm
    @PutMapping("/cases/{caseId}/close")
    @PreAuthorize("hasAuthority('Veterinarian')")
    public ResponseEntity<ApiResponse<MedicalCaseDTO>> closeCase(@PathVariable String caseId,
                                                                 @Valid @RequestBody CaseCloseRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok("Đã đóng ca khám", medicalService.closeCase(caseId, dto.getOutcome())));
    }
}
