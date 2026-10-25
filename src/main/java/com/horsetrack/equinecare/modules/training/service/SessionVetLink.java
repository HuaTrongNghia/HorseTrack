package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.medical.entity.VetCase;
import com.horsetrack.equinecare.modules.medical.repository.VetCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Mã ca khám tạo khi HLV dừng buổi tập (Vet_Cases.training_day_id = session_id). */
@Component
@RequiredArgsConstructor
public class SessionVetLink {

    private final VetCaseRepository vetCaseRepository;

    public Map<Integer, String> caseCodes(Collection<Integer> sessionIds) {
        List<Integer> ids = sessionIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> map = new HashMap<>();
        for (VetCase c : vetCaseRepository.findSessionStops(ids)) {
            if (c.getTrainingDayId() != null && c.getCaseCode() != null) {
                map.putIfAbsent(c.getTrainingDayId(), c.getCaseCode());
            }
        }
        return map;
    }
}
