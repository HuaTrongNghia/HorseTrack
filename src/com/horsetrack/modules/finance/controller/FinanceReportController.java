package com.horsetrack.equinecare.modules.finance.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.finance.dto.FinanceReportDTO;
import com.horsetrack.equinecare.modules.finance.service.FinanceReportService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Báo cáo tài chính (Club Manager, SRS màn hình 26) */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Club Manager')")
public class FinanceReportController {

    private final FinanceReportService financeReportService;

    @GetMapping("/finance")
    @Operation(summary = "Báo cáo tài chính", description = "fromMonth / toMonth dạng yyyy-MM (mặc định: tháng 1 năm nay → tháng hiện tại, tối đa 36 tháng). "
            + "Phát hành và thu theo tháng, tỷ lệ thu, tuổi nợ, doanh thu theo dịch vụ, công nợ theo chủ ngựa, chi phí nhập hàng. Chỉ tính tiền VNPay đã xác minh.")
    public ResponseEntity<ApiResponse<FinanceReportDTO>> finance(@RequestParam(required = false) String fromMonth,
                                                                 @RequestParam(required = false) String toMonth) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy báo cáo tài chính thành công", financeReportService.buildReport(fromMonth, toMonth)));
    }
}
