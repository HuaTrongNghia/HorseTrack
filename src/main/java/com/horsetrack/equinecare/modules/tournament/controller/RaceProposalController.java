package com.horsetrack.equinecare.modules.tournament.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.tournament.dto.*;
import com.horsetrack.equinecare.modules.tournament.service.RaceProposalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/race-proposals")
@RequiredArgsConstructor
public class RaceProposalController {

    private final RaceProposalService raceProposalService;

    // HLV: ngựa được giao; chủ ngựa: ngựa của mình; bác sĩ / quản lý CLB: tất cả
    @GetMapping
    @PreAuthorize("hasAnyAuthority('Trainer', 'Horse Owner', 'Veterinarian', 'Club Manager')")
    public ResponseEntity<ApiResponse<List<RaceProposalDTO>>> list(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách đề xuất thi đấu thành công", raceProposalService.listProposals(status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Horse Owner', 'Veterinarian', 'Club Manager')")
    public ResponseEntity<ApiResponse<RaceProposalDTO>> get(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy đề xuất thi đấu thành công", raceProposalService.getProposal(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('Trainer')")
    public ResponseEntity<ApiResponse<RaceProposalDTO>> create(@Valid @RequestBody RaceProposalCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đã gửi đề xuất thi đấu cho chủ ngựa", raceProposalService.create(request)));
    }

    // Chủ ngựa đồng ý (-> WAITING_VET, chờ bác sĩ khám tiền thi đấu) hoặc từ chối (-> OWNER_DECLINED)
    @PutMapping("/{id}/owner-response")
    @PreAuthorize("hasAuthority('Horse Owner')")
    public ResponseEntity<ApiResponse<RaceProposalDTO>> ownerResponse(@PathVariable Integer id, @Valid @RequestBody OwnerResponseDTO request) {
        RaceProposalDTO dto = raceProposalService.ownerResponse(id, request);
        return ResponseEntity.ok(ApiResponse.ok(Boolean.TRUE.equals(request.getAgree())
                ? "Đã đồng ý. Đề xuất chuyển sang chờ bác sĩ khám tiền thi đấu." : "Đã từ chối đề xuất thi đấu.", dto));
    }

    // HLV đã gửi hồ sơ cho BTC (READY_TO_SUBMIT -> SUBMITTED)
    @PutMapping("/{id}/submitted")
    @PreAuthorize("hasAuthority('Trainer')")
    public ResponseEntity<ApiResponse<RaceProposalDTO>> submitted(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("Đã ghi nhận gửi BTC", raceProposalService.markSubmitted(id)));
    }

    @PutMapping("/{id}/organizer-rejected")
    @PreAuthorize("hasAuthority('Trainer')")
    public ResponseEntity<ApiResponse<RaceProposalDTO>> organizerRejected(@PathVariable Integer id, @Valid @RequestBody OrganizerRejectDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã ghi nhận BTC từ chối", raceProposalService.organizerRejected(id, request)));
    }

    @PutMapping("/{id}/result")
    @PreAuthorize("hasAuthority('Trainer')")
    public ResponseEntity<ApiResponse<RaceProposalDTO>> result(@PathVariable Integer id, @Valid @RequestBody RaceResultDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã ghi kết quả thi đấu", raceProposalService.recordResult(id, request)));
    }
}
