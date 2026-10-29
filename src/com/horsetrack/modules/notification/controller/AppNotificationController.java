package com.horsetrack.equinecare.modules.notification.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.notification.dto.AppNotificationDTO;
import com.horsetrack.equinecare.modules.notification.service.AppNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Chuông thông báo: mỗi user chỉ đọc tin của chính mình. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AppNotificationController {

    private final AppNotificationService appNotificationService;

    @GetMapping
    @Operation(summary = "Danh sách thông báo của tôi")
    public ResponseEntity<ApiResponse<Page<AppNotificationDTO>>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông báo thành công", appNotificationService.mine(page, size)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Số thông báo chưa đọc")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.ok("OK", Map.of("count", appNotificationService.unreadCount())));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Đánh dấu một thông báo đã đọc")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long id) {
        appNotificationService.markRead(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu đọc", null));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Đánh dấu tất cả đã đọc")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead() {
        int n = appNotificationService.markAllRead();
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu đọc", Map.of("updated", n)));
    }
}
