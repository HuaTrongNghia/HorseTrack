package com.horsetrack.controller;

import com.horsetrack.dto.HorseRegistrationDTO;
import com.horsetrack.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/horses")
public class HorseRegistrationController {

    // (Sau này bạn sẽ @Autowired HorseRegistrationService vào đây)

    /**
     * Tác nhân: HORSE OWNER (Chủ ngựa)
     * Nghiệp vụ: Nộp hồ sơ đăng ký ngựa mới (Chờ thẩm định)
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> registerHorse(@RequestBody HorseRegistrationDTO request) {

        // BƯỚC 1: "Tự động kiểm tra form" & "Vi phạm điều kiện biểu mẫu?" theo FLOW 1
        if (request.getHorseName() == null || request.getHorseName().trim().isEmpty()) {
            // "Tiếp nhận thông tin báo lỗi" trả về cho Chủ ngựa
            return ResponseEntity.badRequest().body(
                    new ApiResponse(400, "Lỗi biểu mẫu: Tên ngựa không được để trống!", null)
            );
        }
        if (request.getOriginCertificate() == null) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse(400, "Lỗi biểu mẫu: Thiếu giấy tờ chứng minh nguồn gốc!", null)
            );
        }

        // BƯỚC 2: Nếu form đúng, lưu tạm vào Database với trạng thái "PENDING" (Chờ duyệt)
        // Todo: horseService.submitPendingRegistration(request);

        return ResponseEntity.ok(
                new ApiResponse(200, "Hệ thống đã tiếp nhận hồ sơ! Đang chuyển cho Club Manager thẩm định giấy tờ.", request)
        );
    }

    /**
     * Tác nhân: CLUB MANAGER (Quản lý CLB)
     * Nghiệp vụ: Phê duyệt hồ sơ đủ điều kiện
     */
    @PostMapping("/{horseId}/approve")
    public ResponseEntity<ApiResponse> approveHorseRegistration(@PathVariable String horseId) {
        // BƯỚC 3: "Xác nhận phê duyệt & gán chủ sở hữu" theo FLOW 1
        // Todo: horseService.approveHorse(horseId);

        // BƯỚC 4: "Lưu hồ sơ: Horse + Owner + Ghi Audit Log"
        return ResponseEntity.ok(
                new ApiResponse(200, "Đã phê duyệt! Hệ thống đã lưu hồ sơ và ghi Audit Log thành công.", null)
        );
    }

    /**
     * Tác nhân: CLUB MANAGER (Quản lý CLB)
     * Nghiệp vụ: Từ chối hồ sơ không đủ điều kiện
     */
    @PostMapping("/{horseId}/reject")
    public ResponseEntity<ApiResponse> rejectHorseRegistration(
            @PathVariable String horseId,
            @RequestParam String reason) { // Bắt buộc phải có lý do từ chối

        // BƯỚC 5: "Gửi thông báo từ chối kèm lý do" cho Chủ ngựa theo FLOW 1
        // Todo: horseService.rejectHorse(horseId, reason);

        return ResponseEntity.ok(
                new ApiResponse(200, "Đã từ chối hồ sơ ngựa " + horseId + " với lý do: " + reason, null)
        );
    }
}