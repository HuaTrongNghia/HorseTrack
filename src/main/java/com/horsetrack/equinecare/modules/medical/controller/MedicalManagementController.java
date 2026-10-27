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

    // Thêm thuốc khi phác đồ đang điều trị: kho giữ hàng ngay, chủ ngựa nhận hóa đơn thuốc bổ sung, kho cấp sau khi thanh toán
    @PostMapping("/protocols/{protocolId}/items")
    @PreAuthorize("hasAuthority('Veterinarian')")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> addItemDuringTreatment(@PathVariable Integer protocolId,
            @jakarta.validation.Valid @RequestBody com.horsetrack.equinecare.modules.medical.dto.ProtocolItemAddRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã bổ sung thuốc vào phác đồ", service.addItemDuringTreatment(protocolId, request)));
    }

    @PutMapping("/protocols/{protocolId}/phases/{phaseNo}/complete")
    @PreAuthorize("hasAuthority('Veterinarian')")
    @Operation(summary = "Hoàn thành giai đoạn", description = "Hoàn thành giai đoạn đang điều trị. continueTreatment=true: sang giai đoạn sau nếu chủ ngựa đã thanh toán, chờ thanh toán nếu chưa, hoặc kết thúc phác đồ nếu hết giai đoạn. continueTreatment=false: ngưng phác đồ sau giai đoạn này, hủy hoặc hoàn hóa đơn các giai đoạn chưa làm. Ghi chú bắt buộc khi ngưng.")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> completePhase(@PathVariable Integer protocolId,
                                                                           @PathVariable Integer phaseNo,
                                                                           @Valid @RequestBody(required = false) ProtocolPhaseDecisionDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã hoàn thành giai đoạn " + phaseNo, service.completePhase(protocolId, phaseNo, request)));
    }

    @PutMapping("/protocols/{protocolId}/phases/{phaseNo}/finish-early")
    @PreAuthorize("hasAuthority('Veterinarian')")
    @Operation(summary = "Kết thúc sớm giai đoạn", description = "Bác sĩ kết thúc giai đoạn trước ngày đã định. Giai đoạn trước phải đã xong. Kho phải đã cấp thuốc giai đoạn này. Ghi chú không bắt buộc. Không tự chuyển giai đoạn sau; dùng /complete để chuyển hoặc ngưng phác đồ.")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> finishPhaseEarly(@PathVariable Integer protocolId,
                                                                              @PathVariable Integer phaseNo,
                                                                              @Valid @RequestBody(required = false) ProtocolPhaseDecisionDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã kết thúc sớm giai đoạn " + phaseNo, service.finishPhaseEarly(protocolId, phaseNo, request)));
    }

    @PutMapping("/protocols/{protocolId}/phases/{phaseNo}/start-early")
    @PreAuthorize("hasAuthority('Veterinarian')")
    @Operation(summary = "Bắt đầu giai đoạn", description = "Bắt đầu giai đoạn kế tiếp khi giai đoạn trước đã hoàn thành và giai đoạn này đã thanh toán (ca cấp cứu hoặc giai đoạn không có hóa đơn thì không chờ tiền). Ngày bắt đầu còn ở tương lai thì kéo về hôm nay. Không yêu cầu kho đã cấp thuốc.")
    public ResponseEntity<ApiResponse<TreatmentProtocolDTO>> startPhaseEarly(@PathVariable Integer protocolId,
                                                                             @PathVariable Integer phaseNo,
                                                                             @Valid @RequestBody(required = false) ProtocolPhaseDecisionDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã bắt đầu sớm giai đoạn " + phaseNo, service.startPhaseEarly(protocolId, phaseNo, request)));
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
