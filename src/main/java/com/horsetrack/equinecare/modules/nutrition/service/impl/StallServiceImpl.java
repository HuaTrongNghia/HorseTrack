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

    @Override
    @Transactional(readOnly = true)
    public List<StallResponseDTO> getStallMap() {
        return stallRepository.findAllWithHorse().stream().map(stall -> StallResponseDTO.builder()
                .stallId(stall.getStallId())
                .stallCode(stall.getStallCode())
                .barnZone(stall.getBarnZone())
                .cleanStatus(stall.getCleanStatus())
                .capacity(stall.getCapacity() != null ? stall.getCapacity() : 1)
                .currentHorseId(stall.getHorse() != null ? stall.getHorse().getHorseId() : null)
                .horseName(stall.getHorse() != null ? stall.getHorse().getHorseName() : null)
                .isLocked(stall.getHorse() != null ? stall.getHorse().getIsLocked() : null)
                .horseHealthStatus(stall.getHorse() != null ? stall.getHorse().getHealthStatus() : null)
                .build()
        ).collect(Collectors.toList());
    }
}