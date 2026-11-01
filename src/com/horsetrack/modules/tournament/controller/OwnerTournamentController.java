package com.horsetrack.equinecare.modules.tournament.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.tournament.dto.OwnerTournamentDTO;
import com.horsetrack.equinecare.modules.tournament.service.TournamentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/owner/tournaments")
@RequiredArgsConstructor
public class OwnerTournamentController {

    private final TournamentService tournamentService;

    @GetMapping
    @PreAuthorize("hasAuthority('Horse Owner')")
    @Operation(summary = "Giải đấu cho chủ ngựa (kèm lọc điều kiện)",
            description = "Mỗi giải: eligible, ngựa của chủ (eligible, checks[], reasons[], formScore, xếp theo phong độ, recommended), myProposal, slotsLeft, queueLength, canPropose. open=true chỉ lấy giải còn nhận đề xuất.")
    public ResponseEntity<ApiResponse<List<OwnerTournamentDTO>>> list(@RequestParam(defaultValue = "false") boolean open) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách giải đấu thành công", tournamentService.ownerTournaments(open)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('Horse Owner')")
    @Operation(summary = "Một giải đấu cho chủ ngựa (kèm lọc điều kiện)")
    public ResponseEntity<ApiResponse<OwnerTournamentDTO>> get(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy giải đấu thành công", tournamentService.ownerTournament(id)));
    }
}
