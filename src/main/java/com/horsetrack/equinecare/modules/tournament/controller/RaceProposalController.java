package com.horsetrack.equinecare.modules.tournament.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.tournament.dto.*;
import com.horsetrack.equinecare.modules.tournament.service.RaceNotifier;
import com.horsetrack.equinecare.modules.tournament.service.RaceProposalService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/race-proposals")
@RequiredArgsConstructor
public class RaceProposalController {

    private final RaceProposalService raceProposalService;

    // HLV: ngựa được giao; chủ ngựa: đề xuất của mình; bác sĩ / quản lý CLB: tất cả
    @GetMapping
    @PreAuthorize("hasAnyAuthority('Trainer', 'Horse Owner', 'Veterinarian', 'Club Manager')")
    @Operation(summary = "Danh sách đề xuất thi đấu", description = "HLV thấy ngựa được giao, chủ ngựa thấy đề xuất của mình, bác sĩ và quản lý thấy tất cả. Lọc theo status, complaintStatus (PENDING: khiếu nại chờ duyệt), tournamentId. Mỗi đề xuất có stageDeadline và queuePosition.")
    public ResponseEntity<ApiResponse<List<RaceProposalDTO>>> list(@RequestParam(required = false) String status,
                                                                   @RequestParam(required = false) String complaintStatus,
                                                                   @RequestParam(required = false) Integer tournamentId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách đề xuất thi đấu thành công", raceProposalService.listProposals(status, complaintStatus, tournamentId)));
    }

    @GetMapping("/reject-reasons")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mã lý do HLV từ chối")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> rejectReasons() {
        List<Map<String, String>> list = RaceNotifier.REJECT_REASONS.entrySet().stream()
                .map(e -> Map.of("code", e.getKey(), "label", e.getValue())).toList();
        return ResponseEntity.ok(ApiResponse.ok("Thành công", list));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Horse Owner', 'Veterinarian', 'Club Manager')")
    @Operation(summary = "Chi tiết đề xuất thi đấu")
    public ResponseEntity<ApiResponse<RaceProposalDTO>> get(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy đề xuất thi đấu thành công", raceProposalService.getProposal(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('Horse Owner')")
    @Operation(summary = "Chủ ngựa đề xuất thi đấu", description = "{tournamentId, horseId}. Mỗi chủ ngựa tối đa 1 đề xuất còn hiệu lực / giải; ngựa phải đủ điều kiện. Còn suất thì giữ chỗ (WAITING_TRAINER, HLV xác nhận trong 2 ngày), hết suất thì vào hàng chờ (WAITLISTED).")
    public ResponseEntity<ApiResponse<RaceProposalDTO>> create(@Valid @RequestBody RaceProposalCreateDTO request) {
        RaceProposalDTO dto = raceProposalService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(Boolean.TRUE.equals(dto.getHoldingSlot())
                ? "Đã giữ chỗ, chờ HLV xác nhận" : "Đã hết suất, đề xuất vào hàng chờ vị trí " + dto.getQueuePosition(), dto));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('Horse Owner')")
    @Operation(summary = "Chủ ngựa hủy đề xuất", description = "Hủy đề xuất đang ở hàng chờ hoặc đang giữ chỗ (chưa thanh toán). Suất trống được đôn cho người đầu hàng chờ.")
    public ResponseEntity<ApiResponse<RaceProposalDTO>> cancel(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("Đã hủy đề xuất", raceProposalService.cancel(id)));
    }

    @PutMapping("/{id}/trainer-decision")
    @PreAuthorize("hasAuthority('Trainer')")
    @Operation(summary = "HLV xác nhận / từ chối", description = "{approve, reasonCode, note}. Xác nhận: sang WAITING_VET_EXAM (bác sĩ khám trong 3 ngày). Từ chối bắt buộc reasonCode (GET /race-proposals/reject-reasons), mất suất và đôn hàng chờ.")
    public ResponseEntity<ApiResponse<RaceProposalDTO>> trainerDecision(@PathVariable Integer id, @Valid @RequestBody TrainerDecisionDTO request) {
        RaceProposalDTO dto = raceProposalService.trainerDecision(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Đề xuất chuyển sang: " + dto.getStatusLabel(), dto));
    }

    @PostMapping("/{id}/complaint")
    @PreAuthorize("hasAuthority('Horse Owner')")
    @Operation(summary = "Chủ ngựa khiếu nại HLV từ chối", description = "Chỉ cho đề xuất TRAINER_REJECTED, mỗi đề xuất 1 lần, trước hạn đăng ký.")
    public ResponseEntity<ApiResponse<RaceProposalDTO>> complaint(@PathVariable Integer id, @Valid @RequestBody ComplaintDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đã gửi khiếu nại lên Quản lý CLB", raceProposalService.complaint(id, request)));
    }

    @PutMapping("/{id}/complaint-decision")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Quản lý CLB duyệt khiếu nại", description = "{accept, note}. Chấp nhận: đề xuất về WAITING_VET_EXAM và giữ nguyên suất. Bác: giữ TRAINER_REJECTED.")
    public ResponseEntity<ApiResponse<RaceProposalDTO>> complaintDecision(@PathVariable Integer id, @Valid @RequestBody ComplaintDecisionDTO request) {
        RaceProposalDTO dto = raceProposalService.complaintDecision(id, request);
        return ResponseEntity.ok(ApiResponse.ok(Boolean.TRUE.equals(request.getAccept()) ? "Đã chấp nhận khiếu nại" : "Đã bác khiếu nại", dto));
    }

    @GetMapping("/{id}/dossier")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Horse Owner', 'Veterinarian', 'Club Manager')")
    @Operation(summary = "Hồ sơ đăng ký thi đấu", description = "Tổng hợp ngựa, chủ ngựa, HLV, lịch sử tiêm phòng, kết quả khám, thanh toán. Chỉ xem trên màn hình (không xuất PDF).")
    public ResponseEntity<ApiResponse<RaceDossierDTO>> dossier(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy hồ sơ đăng ký thành công", raceProposalService.dossier(id)));
    }
}
