package com.horsetrack.equinecare.modules.finance.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.finance.dto.RefundConfirmDTO;
import com.horsetrack.equinecare.modules.finance.dto.RefundDTO;
import com.horsetrack.equinecare.modules.finance.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing/refunds")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('Club Manager')")
@Tag(name = "Hoàn tiền")
public class RefundController {

    private final RefundService refundService;

    @GetMapping
    @Operation(summary = "Các khoản cần hoàn / đã hoàn",
            description = "status=PENDING (chờ hoàn, cũ nhất trước) hoặc DONE (đã hoàn); bỏ trống = tất cả, mới nhất trước. "
                    + "Khoản chờ hoàn được tạo tự động khi hủy giai đoạn phác đồ đã trả, ngựa bị khóa khi đã đóng lệ phí giải, "
                    + "hoặc lệ phí giải được thanh toán sau khi đề xuất đã đóng.")
    public ResponseEntity<ApiResponse<List<RefundDTO>>> list(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách hoàn tiền", refundService.list(status)));
    }

    @PutMapping("/{refundId}/confirm")
    @Operation(summary = "Xác nhận đã chuyển tiền hoàn",
            description = "Bắt buộc mã giao dịch, ngày chuyển (không ở tương lai) và người chuyển. Hóa đơn REFUND_PENDING chuyển REFUNDED, chủ ngựa nhận thông báo.")
    public ResponseEntity<ApiResponse<RefundDTO>> confirm(@PathVariable Integer refundId, @Valid @RequestBody RefundConfirmDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã xác nhận hoàn tiền", refundService.confirm(refundId, request)));
    }
}
