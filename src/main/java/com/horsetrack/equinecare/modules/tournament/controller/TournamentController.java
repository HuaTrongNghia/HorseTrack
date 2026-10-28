package com.horsetrack.equinecare.modules.tournament.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.tournament.dto.TournamentCreateDTO;
import com.horsetrack.equinecare.modules.tournament.dto.TournamentDTO;
import com.horsetrack.equinecare.modules.tournament.service.RaceProposalService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final RaceProposalService raceProposalService;

    // Danh sách giải đấu; open=true chỉ lấy giải còn nhận đề xuất
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách giải đấu", description = "open=true chỉ lấy giải còn nhận đề xuất.")
    public ResponseEntity<ApiResponse<List<TournamentDTO>>> list(@RequestParam(defaultValue = "false") boolean open) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách giải đấu thành công", raceProposalService.listTournaments(open)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Thêm giải đấu")
    public ResponseEntity<ApiResponse<TournamentDTO>> create(@Valid @RequestBody TournamentCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đã thêm giải đấu", raceProposalService.createTournament(request)));
    }
}
