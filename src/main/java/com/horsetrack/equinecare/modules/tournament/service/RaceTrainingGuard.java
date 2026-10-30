package com.horsetrack.equinecare.modules.tournament.service;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.tournament.entity.CompetitionEntry;
import com.horsetrack.equinecare.modules.tournament.repository.CompetitionEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * An toàn giáo án trước giải: ngựa có đề xuất đã được HLV xác nhận (WAITING_VET_EXAM trở đi) thì trong 7 ngày trước
 * ngày thi (tính cả ngày thi) không được giao giáo án / phân công buổi tập cường độ Cao.
 */
@Component
@RequiredArgsConstructor
public class RaceTrainingGuard {

    public static final int SAFE_DAYS = 7;
    private static final String HIGH = "HIGH";

    private final CompetitionEntryRepository entryRepository;

    /** Cường độ (đã chuẩn hóa LOW / MEDIUM / HIGH) áp dụng cho các ngày [from, to]; vi phạm thì báo lỗi kèm lý do */
    public void assertAllowed(Horse horse, LocalDate from, LocalDate to, String intensity, String action) {
        if (horse == null || from == null || to == null || !HIGH.equalsIgnoreCase(intensity)) {
            return;
        }
        // Những ngày đã qua không còn ảnh hưởng
        LocalDate today = LocalDate.now();
        if (from.isBefore(today)) {
            from = today;
        }
        if (to.isBefore(from)) {
            return;
        }
        for (CompetitionEntry e : entryRepository.findByHorseAndRaceDateBetween(horse.getHorseId(), CompetitionEntry.TRAINER_CONFIRMED, from, to.plusDays(SAFE_DAYS))) {
            LocalDate race = e.getCompetition().getStartDate();
            LocalDate windowStart = race.minusDays(SAFE_DAYS);
            if (!to.isBefore(windowStart) && !from.isAfter(race)) {
                throw new IllegalArgumentException("Không thể " + action + " cường độ Cao: ngựa " + horse.getHorseName() + " đã được HLV xác nhận thi giải "
                        + e.getCompetition().getCompetitionName() + " ngày " + race + " (đề xuất " + RaceNotifier.code(e) + ", "
                        + RaceNotifier.label(e.getStatus()) + "). Trong " + SAFE_DAYS + " ngày trước ngày thi (từ " + windowStart + " đến " + race
                        + ") chỉ được tập cường độ Thấp hoặc Trung bình.");
            }
        }
    }
}
