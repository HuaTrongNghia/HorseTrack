package com.horsetrack.equinecare.modules.notification.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.notification.dto.EmailLogDTO;
import com.horsetrack.equinecare.modules.notification.service.EmailLogService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** F2: Quản lý CLB xem trạng thái gửi email */
@RestController
@RequestMapping("/api/v1/email-logs")
@RequiredArgsConstructor
public class EmailLogController {

    private final EmailLogService emailLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Nhật ký gửi email", description = "Lọc tùy chọn theo userId, purpose (OTP, STAFF_ACCOUNT, BILLING...), status (SENT / FAILED / SKIPPED), toEmail (chứa chuỗi), fromDate / toDate. Mới nhất trước.")
    public ResponseEntity<ApiResponse<Page<EmailLogDTO>>> search(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String toEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy nhật ký gửi email thành công",
                emailLogService.search(userId, purpose, status, toEmail, fromDate, toDate, page, size)));
    }
}
