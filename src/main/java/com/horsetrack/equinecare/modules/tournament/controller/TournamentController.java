package com.horsetrack.equinecare.modules.tournament.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.tournament.dto.RaceProposalDTO;
import com.horsetrack.equinecare.modules.tournament.dto.RaceResultItemDTO;
import com.horsetrack.equinecare.modules.tournament.dto.TournamentCreateDTO;
import com.horsetrack.equinecare.modules.tournament.dto.TournamentDTO;
import com.horsetrack.equinecare.modules.tournament.service.TournamentService;
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

    private final TournamentService tournamentService;

    // Quản lý CLB thấy cả giải nháp; vai trò khác chỉ thấy giải đã công bố / đã kết thúc
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách giải đấu", description = "open=true chỉ lấy giải còn nhận đề xuất. Quản lý CLB thấy cả giải Nháp (DRAFT).")
    public ResponseEntity<ApiResponse<List<TournamentDTO>>> list(@RequestParam(defaultValue = "false") boolean open) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách giải đấu thành công", tournamentService.list(open)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết giải đấu", description = "Gồm điều kiện, bảng thưởng, suất còn lại (slotsLeft) và độ dài hàng chờ (queueLength).")
    public ResponseEntity<ApiResponse<TournamentDTO>> get(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy giải đấu thành công", tournamentService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Tạo giải đấu (Nháp)", description = "Điều kiện tuổi / cân nặng / giống, số suất, bảng thưởng, ngày thi, hạn đăng ký, lệ phí. Giải mới ở trạng thái DRAFT.")
    public ResponseEntity<ApiResponse<TournamentDTO>> create(@Valid @RequestBody TournamentCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đã tạo giải đấu (Nháp)", tournamentService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Sửa giải đấu", description = "Giải đã có người đề xuất thì không sửa được điều kiện, ngày thi, hạn đăng ký, lệ phí; chỉ được tăng số suất (hàng chờ tự được đôn lên).")
    public ResponseEntity<ApiResponse<TournamentDTO>> update(@PathVariable Integer id, @Valid @RequestBody TournamentCreateDTO request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật giải đấu", tournamentService.update(id, request)));
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Công bố giải đấu", description = "DRAFT sang PUBLISHED; chủ ngựa bắt đầu đề xuất được và nhận thông báo.")
    public ResponseEntity<ApiResponse<TournamentDTO>> publish(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("Đã công bố giải đấu", tournamentService.publish(id)));
    }

    @PutMapping("/{id}/finish")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Kết thúc giải (đã thi xong)", description = "Dùng được cả trước ngày thi (demo). Ngừng nhận đề xuất; đề xuất còn ở hàng chờ / chờ HLV / chờ khám / chờ thanh toán chuyển NO_SLOT (hủy hóa đơn lệ phí chưa trả), "
            + "chỉ giữ đề xuất REGISTERED để nhập kết quả. Không có đề xuất REGISTERED thì giải chuyển FINISHED luôn.")
    public ResponseEntity<ApiResponse<TournamentDTO>> finish(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok("Đã kết thúc giải", tournamentService.finish(id)));
    }

    @PutMapping("/{id}/results")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Nhập kết quả giải", description = "Nhập được bất cứ lúc nào khi giải đang công bố, kể cả trước ngày thi (để test). Lần nhập đầu tự kết thúc giải như PUT /finish (đề xuất chưa hoàn tất → NO_SLOT). Chỉ cho đề xuất REGISTERED. Tự tính tiền thưởng theo bảng thưởng, cập nhật thành tích ngựa, báo chủ ngựa. Mọi đề xuất đã có kết quả thì giải chuyển FINISHED.")
    public ResponseEntity<ApiResponse<List<RaceProposalDTO>>> results(@PathVariable Integer id, @RequestBody List<@Valid RaceResultItemDTO> request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã nhập kết quả", tournamentService.recordResults(id, request)));
    }
}
