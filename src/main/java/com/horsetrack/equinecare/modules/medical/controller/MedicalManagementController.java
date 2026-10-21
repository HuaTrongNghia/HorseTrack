package com.horsetrack.equinecare.modules.medical.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.medical.dto.*;
import com.horsetrack.equinecare.modules.medical.service.MedicalManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/medical-management", "/api/medical-management"})
@CrossOrigin("*")
@RequiredArgsConstructor
@Tag(name = "Treatment Protocols (Veterinarian)", description = "Danh mục thuốc và phác đồ điều trị: lập, xác nhận phương án kho, chờ hàng, phiên bản mới, tiến trình, kết thúc (BR_07)")
public class MedicalManagementController {

    private final MedicalManagementService service;

    @GetMapping("/medications")
    @PreAuthorize("hasAnyAuthority('Veterinarian', 'Club Manager')")
    @Operation(summary = "Danh mục thuốc (chỉ đọc)", description = "Vật tư nhóm MEDICINE, VACCINE, MEDICAL_SUPPLY kèm khả dụng, đường dùng, thuốc kiểm soát, thời gian ngưng thuốc.")
    public ResponseEntity<ApiResponse<List<MedicineCatalogDTO>>> getMedications() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh mục thuốc thành công", service.getMedicineCatalog()));
    }

    @PostMapping("/protocols")
    @PreAuthorize("hasAuthority('Veterinarian')")
    @Operation(summary = "Lập phác đồ điều trị", description = "Phác đồ được gửi kho kiểm hàng ngay (WAITING_STOCK_CHECK).")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> createProtocol(@Valid @RequestBody ProtocolCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã lập phác đồ và gửi kho kiểm hàng", service.createProtocol(request)));
    }

    @GetMapping("/protocols")
    @PreAuthorize("hasAnyAuthority('Veterinarian', 'Club Manager')")
    @Operation(summary = "Danh sách phác đồ", description = "Lọc theo state và horseId. Club Manager chỉ xem (BR_07).")
    public ResponseEntity<ApiResponse<List<TreatmentProtocolDTO>>> getProtocols(@RequestParam(required = false) String state,
                                                                                @RequestParam(required = false) Integer horseId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách phác đồ thành công", service.getProtocols(state, horseId)));
    }

    @GetMapping("/protocols/{protocolId}")
    @PreAuthorize("hasAnyAuthority('Veterinarian', 'Club Manager')")
    @Operation(summary = "Chi tiết phác đồ", description = "Giai đoạn, thuốc, kết quả kiểm kho (stockAnswer), phiên bản trước/sau, ghi chú tiến trình.")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> getProtocolDetail(@PathVariable Integer protocolId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết phác đồ thành công", service.getProtocolDetail(protocolId)));
    }

    @PutMapping("/protocols/{protocolId}/confirm")
    @PreAuthorize("hasAuthority('Veterinarian')")
    @Operation(summary = "Xác nhận phương án kho", description = "Khi kho đã giữ đủ hàng (STOCK_AVAILABLE) -> VET_CONFIRMED, kho được cấp thuốc.")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> confirm(@PathVariable Integer protocolId) {
        return ResponseEntity.ok(ApiResponse.ok("Đã xác nhận phác đồ, kho có thể cấp thuốc", service.confirm(protocolId)));
    }

    @PutMapping("/protocols/{protocolId}/wait-restock")
    @PreAuthorize("hasAuthority('Veterinarian')")
    @Operation(summary = "Chờ hàng về", description = "Khi kho báo thiếu hoặc hàng về trễ: giữ nguyên phác đồ, chờ kho kiểm lại khi hàng về.")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> waitForRestock(@PathVariable Integer protocolId,
                                                                            @Valid @RequestBody(required = false) ProtocolWaitRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã chuyển phác đồ sang chờ hàng", service.waitForRestock(protocolId, request)));
    }

    @PostMapping("/protocols/{protocolId}/new-version")
    @PreAuthorize("hasAuthority('Veterinarian')")
    @Operation(summary = "Tạo phiên bản mới", description = "Đổi thuốc/số lượng khi thiếu hàng. Bản cũ SUPERSEDED và trả hàng đã giữ; bản mới gửi kho kiểm lại.")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> createNewVersion(@PathVariable Integer protocolId,
                                                                              @Valid @RequestBody ProtocolCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã tạo phiên bản mới của phác đồ", service.createNewVersion(protocolId, request)));
    }

    @PostMapping("/protocols/{protocolId}/progress-notes")
    @PreAuthorize("hasAuthority('Veterinarian')")
    @Operation(summary = "Ghi tiến trình điều trị", description = "Chỉ khi phác đồ đang điều trị (IN_TREATMENT).")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> addProgressNote(@PathVariable Integer protocolId,
                                                                             @Valid @RequestBody ProtocolTextRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã ghi tiến trình điều trị", service.addProgressNote(protocolId, request)));
    }

    @PutMapping("/protocols/{protocolId}/complete")
    @PreAuthorize("hasAuthority('Veterinarian')")
    @Operation(summary = "Kết thúc điều trị", description = "Ghi kết quả điều trị (content) và đóng phác đồ (COMPLETED).")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> complete(@PathVariable Integer protocolId,
                                                                      @Valid @RequestBody ProtocolTextRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã kết thúc phác đồ điều trị", service.complete(protocolId, request)));
    }

    @PutMapping("/protocols/{protocolId}/cancel")
    @PreAuthorize("hasAuthority('Veterinarian')")
    @Operation(summary = "Hủy phác đồ", description = "Chỉ khi chưa cấp thuốc. Hàng đã giữ được trả lại kho. content = lý do hủy.")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> cancel(@PathVariable Integer protocolId,
                                                                    @Valid @RequestBody ProtocolTextRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã hủy phác đồ", service.cancel(protocolId, request)));
    }
}
