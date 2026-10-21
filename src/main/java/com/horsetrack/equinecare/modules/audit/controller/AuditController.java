package com.horsetrack.equinecare.modules.audit.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.audit.dto.AuditLogResponseDTO;
import com.horsetrack.equinecare.modules.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Các API truy vấn nhật ký kiểm toán hệ thống (BR_10)")
public class AuditController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Xem nhật ký hệ thống gần nhất", description = "Chỉ dành cho Club Manager kiểm tra audit log.")
    public ResponseEntity<ApiResponse<List<AuditLogResponseDTO>>> getRecentLogs() {
        List<AuditLogResponseDTO> logs = auditLogService.getRecentLogs();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách nhật ký kiểm toán thành công", logs));
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Xem nhật ký phân trang", description = "Phân trang audit log.")
    public ResponseEntity<ApiResponse<Page<AuditLogResponseDTO>>> getLogsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AuditLogResponseDTO> logPage = auditLogService.getLogsPaged(page, size);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách nhật ký phân trang thành công", logPage));
    }
}
