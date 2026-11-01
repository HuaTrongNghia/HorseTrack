package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.training.dto.SessionReadingDTO;
import com.horsetrack.equinecare.modules.training.entity.SessionReading;
import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import com.horsetrack.equinecare.modules.training.repository.SessionReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Ngưỡng và các lần đo của buổi tập để trả về cho FE (chi tiết giáo án, lịch tập) */
@Component
@RequiredArgsConstructor
public class SessionReadingView {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final SessionReadingRepository readingRepository;

    @Value("${app.training.default-max-heart-rate:180}")
    private int defaultMaxHeartRate;

    @Value("${app.training.default-max-speed-kmh:60}")
    private BigDecimal defaultMaxSpeedKmh;

    public int maxHeartRate(TrainingSession s) {
        return s.getMaxHeartRate() != null ? s.getMaxHeartRate() : defaultMaxHeartRate;
    }

    public BigDecimal maxSpeedKmh(TrainingSession s) {
        return s.getMaxSpeedKmh() != null ? s.getMaxSpeedKmh() : defaultMaxSpeedKmh;
    }

    /** Các lần đo của nhiều buổi tập, gom theo sessionId (một truy vấn) */
    public Map<Integer, List<SessionReading>> load(Collection<TrainingSession> sessions) {
        List<Integer> ids = sessions.stream().map(TrainingSession::getSessionId).filter(Objects::nonNull).toList();
        Map<Integer, List<SessionReading>> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (SessionReading r : readingRepository.findBySession_SessionIdInOrderByRecordedAtAscReadingIdAsc(ids)) {
            map.computeIfAbsent(r.getSession().getSessionId(), k -> new ArrayList<>()).add(r);
        }
        return map;
    }

    public List<SessionReadingDTO> phase(List<SessionReading> readings, String phase) {
        if (readings == null) {
            return List.of();
        }
        return readings.stream().filter(r -> phase.equals(r.getReadingPhase())).map(SessionReadingView::toDto).toList();
    }

    public static SessionReadingDTO toDto(SessionReading r) {
        return SessionReadingDTO.builder()
                .readingId(r.getReadingId())
                .time(r.getRecordedAt() != null ? r.getRecordedAt().format(HHMM) : null)
                .recordedAt(r.getRecordedAt())
                .heartRate(r.getHeartRateBpm())
                .speedKmh(r.getSpeedKmh())
                .build();
    }
}
