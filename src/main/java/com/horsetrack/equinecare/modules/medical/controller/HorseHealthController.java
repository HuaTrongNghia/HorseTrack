package com.horsetrack.equinecare.modules.medical.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.dto.GroomZoneResponseDTO;
import com.horsetrack.equinecare.modules.auth.repository.GroomProfileRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.horse.service.HorseAccessGuard;
import com.horsetrack.equinecare.modules.medical.dto.HealthStatusUpdateDTO;
import com.horsetrack.equinecare.modules.medical.dto.InjuryCreateDTO;
import com.horsetrack.equinecare.modules.medical.dto.InjuryDTO;
import com.horsetrack.equinecare.modules.medical.service.HorseHealthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Chấn thương, trạng thái sức khỏe và Groom theo khu của một con ngựa */
@RestController
@RequestMapping("/api/v1/horses")
@RequiredArgsConstructor
public class HorseHealthController {

    private final HorseHealthService horseHealthService;
    private final HorseRepository horseRepository;
    private final HorseAccessGuard horseAccessGuard;
    private final GroomProfileRepository groomProfileRepository;

    @GetMapping("/{id}/injuries")
    @PreAuthorize("hasAnyAuthority('Club Manager', 'Trainer', 'Veterinarian', 'Groom', 'Horse Owner')")
    public ResponseEntity<ApiResponse<List<InjuryDTO>>> listInjuries(@PathVariable Integer id) {
        Horse horse = findHorse(id);
        horseAccessGuard.checkAssigned(horse);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean owner = auth != null && auth.getAuthorities().stream().anyMatch(a -> "Horse Owner".equals(a.getAuthority()));
        if (owner && (horse.getOwner() == null
                || (!auth.getName().equals(horse.getOwner().getUsername()) && !auth.getName().equalsIgnoreCase(String.valueOf(horse.getOwner().getEmail()))))) {
            throw new AccessDeniedException("Bạn chỉ xem được chấn thương của ngựa thuộc sở hữu của mình.");
        }
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách chấn thương thành công", horseHealthService.listForHorse(id)));
    }

    @PostMapping("/{id}/injuries")
    @PreAuthorize("hasAuthority('Veterinarian')")
    public ResponseEntity<ApiResponse<InjuryDTO>> addInjury(@PathVariable Integer id, @Valid @RequestBody InjuryCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đã đánh dấu chấn thương", horseHealthService.addInjury(id, request)));
    }

    @DeleteMapping("/{id}/injuries/{injuryId}")
    @PreAuthorize("hasAuthority('Veterinarian')")
    public ResponseEntity<ApiResponse<Void>> deleteInjury(@PathVariable Integer id, @PathVariable Integer injuryId) {
        horseHealthService.deleteInjury(id, injuryId);
        return ResponseEntity.ok(ApiResponse.ok("Đã gỡ đánh dấu chấn thương", null));
    }

    @PutMapping("/{id}/health-status")
    @PreAuthorize("hasAuthority('Veterinarian')")
    public ResponseEntity<ApiResponse<String>> updateHealthStatus(@PathVariable Integer id, @Valid @RequestBody HealthStatusUpdateDTO request) {
        String status = horseHealthService.updateHealthStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật trạng thái sức khỏe", status));
    }

    // Groom đang hoạt động phụ trách khu chuồng của ngựa (HLV chọn người khi giao việc chăm sóc / buổi tập)
    @GetMapping("/{id}/grooms")
    @PreAuthorize("hasAnyAuthority('Trainer', 'Club Manager')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<GroomZoneResponseDTO>>> grooms(@PathVariable Integer id) {
        Horse horse = findHorse(id);
        horseAccessGuard.checkAssigned(horse);
        if (horse.getStall() == null || horse.getStall().getBarnZone() == null) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " chưa được cấp chuồng nên chưa xác định được khu.");
        }
        String zone = horse.getStall().getBarnZone().trim();
        List<GroomZoneResponseDTO> grooms = groomProfileRepository.findActiveGroomsInZone(zone).stream()
                .map(gp -> GroomZoneResponseDTO.builder()
                        .userId(gp.getUserId())
                        .fullName(gp.getUser().getFullName())
                        .barnZone(gp.getAssignedBarnZone())
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Groom phụ trách " + zone, grooms));
    }

    private Horse findHorse(Integer id) {
        return horseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + id));
    }
}
