package com.horsetrack.equinecare.modules.nutrition.service.impl;

import com.horsetrack.equinecare.modules.horse.repository.StallRepository;
import com.horsetrack.equinecare.modules.nutrition.dto.StallResponseDTO;
import com.horsetrack.equinecare.modules.nutrition.service.StallService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StallServiceImpl implements StallService {

    private final StallRepository stallRepository;
    private final com.horsetrack.equinecare.modules.auth.repository.UserRepository userRepository;
    private final com.horsetrack.equinecare.modules.auth.repository.GroomProfileRepository groomProfileRepository;
    private final com.horsetrack.equinecare.modules.audit.service.AuditTrail auditTrail;

    @Override
    @Transactional(readOnly = true)
    public List<StallResponseDTO> getStallMap() {
        return stallRepository.findAllWithHorse().stream().map(StallServiceImpl::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StallResponseDTO updateCleanStatus(Integer stallId, com.horsetrack.equinecare.modules.nutrition.dto.StallCleanStatusDTO request) {
        var stall = stallRepository.findById(stallId)
                .orElseThrow(() -> new com.horsetrack.equinecare.common.exception.ResourceNotFoundException("Không tìm thấy chuồng với ID: " + stallId));
        String username = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new com.horsetrack.equinecare.common.exception.ResourceNotFoundException("Không tìm thấy người dùng: " + username));
        if ("Groom".equals(user.getRoleName())) {
            String zone = groomProfileRepository.findById(user.getUserId()).map(gp -> gp.getAssignedBarnZone()).orElse(null);
            if (zone == null || stall.getBarnZone() == null || !zone.trim().equalsIgnoreCase(stall.getBarnZone().trim())) {
                throw new org.springframework.security.access.AccessDeniedException("Chuồng " + stall.getStallCode() + " không thuộc khu bạn phụ trách");
            }
        }
        String status = request.getStatus().trim().toUpperCase();
        String before = stall.getCleanStatus();
        if (status.equals(before)) {
            throw new IllegalArgumentException("Chuồng " + stall.getStallCode() + " đã ở trạng thái " + status + ".");
        }
        stall.setCleanStatus(status);
        stall.setUpdatedAt(java.time.LocalDateTime.now());
        stallRepository.save(stall);
        auditTrail.record("UPDATE_STALL_STATUS", "Stalls", "clean_status=" + before,
                "clean_status=" + status + " | chuồng " + stall.getStallCode()
                        + (request.getNote() != null && !request.getNote().isBlank() ? " | " + request.getNote().trim() : ""));
        return toDto(stall);
    }

    @Override
    @Transactional
    public void markCleanedByTask(com.horsetrack.equinecare.modules.horse.entity.Stall stall, String taskLabel) {
        if (stall == null || "CLEAN".equals(stall.getCleanStatus()) || "MAINTENANCE".equals(stall.getCleanStatus())) {
            return;
        }
        String before = stall.getCleanStatus();
        stall.setCleanStatus("CLEAN");
        stall.setUpdatedAt(java.time.LocalDateTime.now());
        stallRepository.save(stall);
        auditTrail.record("UPDATE_STALL_STATUS", "Stalls", "clean_status=" + before,
                "clean_status=CLEAN | chuồng " + stall.getStallCode() + " | hoàn thành " + taskLabel);
    }

    private static StallResponseDTO toDto(com.horsetrack.equinecare.modules.horse.entity.Stall stall) {
        return StallResponseDTO.builder()
                .stallId(stall.getStallId())
                .stallCode(stall.getStallCode())
                .barnZone(stall.getBarnZone())
                .cleanStatus(stall.getCleanStatus())
                .capacity(stall.getCapacity() != null ? stall.getCapacity() : 1)
                .currentHorseId(stall.getHorse() != null ? stall.getHorse().getHorseId() : null)
                .horseName(stall.getHorse() != null ? stall.getHorse().getHorseName() : null)
                .isLocked(stall.getHorse() != null ? stall.getHorse().getIsLocked() : null)
                .horseHealthStatus(stall.getHorse() != null ? stall.getHorse().getHealthStatus() : null)
                .build();
    }
}