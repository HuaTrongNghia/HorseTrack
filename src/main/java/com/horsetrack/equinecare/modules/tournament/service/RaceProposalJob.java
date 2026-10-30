package com.horsetrack.equinecare.modules.tournament.service;

import com.horsetrack.equinecare.modules.tournament.entity.CompetitionEntry;
import com.horsetrack.equinecare.modules.tournament.entity.ExternalCompetition;
import com.horsetrack.equinecare.modules.tournament.repository.CompetitionEntryRepository;
import com.horsetrack.equinecare.modules.tournament.repository.ExternalCompetitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.horsetrack.equinecare.modules.tournament.service.RaceNotifier.esc;
import static com.horsetrack.equinecare.modules.tournament.service.RaceNotifier.fmt;

/**
 * Job giải đấu (mặc định 15 phút/lần), với từng giải đang công bố:
 * 1. Hết hạn chặng → *_EXPIRED, đôn người đầu hàng chờ lên;
 * 2. Kiểm tra lại điều kiện ngựa của đề xuất đang ở hàng chờ / đang chạy chặng → INELIGIBLE;
 * 3. Nhắc sắp hết hạn chặng (mặc định trước 12 giờ);
 * 4. Đôn hàng chờ nếu còn suất trống;
 * 5. Đến hạn đăng ký: hàng chờ còn lại → NO_SLOT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaceProposalJob {

    private static final Map<String, String> EXPIRED = Map.of(
            CompetitionEntry.WAITING_TRAINER, CompetitionEntry.TRAINER_EXPIRED,
            CompetitionEntry.WAITING_VET_EXAM, CompetitionEntry.VET_EXPIRED,
            CompetitionEntry.WAITING_PAYMENT, CompetitionEntry.PAYMENT_EXPIRED);
    private static final Map<String, String> STAGE = Map.of(
            CompetitionEntry.WAITING_TRAINER, "HLV xác nhận",
            CompetitionEntry.WAITING_VET_EXAM, "bác sĩ khám tiền thi đấu",
            CompetitionEntry.WAITING_PAYMENT, "thanh toán lệ phí");
    private static final List<String> RECHECK = List.of(CompetitionEntry.WAITLISTED, CompetitionEntry.WAITING_TRAINER,
            CompetitionEntry.WAITING_VET_EXAM, CompetitionEntry.WAITING_PAYMENT);

    @Value("${app.tournament.reminder-hours:12}")
    private long reminderHours;

    private final ExternalCompetitionRepository competitionRepository;
    private final CompetitionEntryRepository entryRepository;
    private final HorseEligibilityService eligibility;
    private final RaceSlotAllocator slots;
    private final RaceNotifier notifier;

    @Scheduled(fixedDelayString = "${app.tournament.job-ms:900000}", initialDelayString = "${app.tournament.job-initial-delay-ms:120000}")
    @Transactional
    public void runJob() {
        Map<String, Integer> r = run(LocalDateTime.now());
        if (r.values().stream().anyMatch(v -> v > 0)) {
            log.info("Job giải đấu: {}", r);
        }
    }

    /** Chạy một lượt tại thời điểm now; trả về số đề xuất theo từng loại xử lý */
    @Transactional
    public Map<String, Integer> run(LocalDateTime now) {
        Map<String, Integer> r = new LinkedHashMap<>();
        r.put("expired", 0);
        r.put("ineligible", 0);
        r.put("reminded", 0);
        r.put("promoted", 0);
        r.put("noSlot", 0);
        for (ExternalCompetition c : competitionRepository.findByStatusOrderByStartDateAsc(ExternalCompetition.PUBLISHED)) {
            Integer cid = c.getCompetitionId();

            for (CompetitionEntry e : entries(cid, CompetitionEntry.IN_STAGE)) {
                if (e.getStageDeadline() == null || now.isBefore(e.getStageDeadline())) continue;
                // Đang có giao dịch VNPay chờ xác minh: đợi kết quả thanh toán
                if (e.getInvoice() != null && RaceSlotAllocator.hasLiveTransaction(e.getInvoice())) continue;
                slots.close(e, EXPIRED.get(e.getStatus()), "Quá hạn " + STAGE.get(e.getStatus()) + " (hạn " + fmt(e.getStageDeadline()) + ")",
                        "RACE_AUTO_EXPIRE", now);
                inc(r, "expired");
            }

            for (CompetitionEntry e : entries(cid, RECHECK)) {
                if (!RECHECK.contains(e.getStatus())) continue; // đã bị đóng ở bước trên / do đôn hàng chờ
                if (e.getInvoice() != null && RaceSlotAllocator.hasLiveTransaction(e.getInvoice())) continue;
                // HLV đã xác nhận thì việc đổi / bỏ HLV phụ trách không làm mất suất
                Set<String> ignore = CompetitionEntry.TRAINER_CONFIRMED.contains(e.getStatus()) ? Set.of("HAS_TRAINER") : Set.of();
                List<String> fail = eligibility.failures(e.getHorse(), c, ignore);
                if (!fail.isEmpty()) {
                    slots.close(e, CompetitionEntry.INELIGIBLE, "Ngựa không còn đủ điều kiện: " + String.join("; ", fail), "RACE_AUTO_INELIGIBLE", now);
                    inc(r, "ineligible");
                }
            }

            for (CompetitionEntry e : entries(cid, CompetitionEntry.IN_STAGE)) {
                if (Boolean.TRUE.equals(e.getDeadlineReminded()) || e.getStageDeadline() == null
                        || now.plusHours(reminderHours).isBefore(e.getStageDeadline())) continue;
                remind(e);
                e.setDeadlineReminded(true);
                entryRepository.save(e);
                inc(r, "reminded");
            }

            r.merge("promoted", slots.promoteWaitlist(c, now), Integer::sum);

            if (!now.isBefore(c.registrationCloseAt())) {
                for (CompetitionEntry e : entries(cid, List.of(CompetitionEntry.WAITLISTED))) {
                    slots.close(e, CompetitionEntry.NO_SLOT, "Hết hạn đăng ký (" + c.getRegistrationDeadline() + ") mà chưa đến lượt lên suất chính thức",
                            "RACE_AUTO_NO_SLOT", now);
                    inc(r, "noSlot");
                }
            }
        }
        return r;
    }

    private void remind(CompetitionEntry e) {
        String subject = "[HorseTrack] Sắp hết hạn " + STAGE.get(e.getStatus()) + " - đề xuất " + RaceNotifier.code(e);
        String html = "<p>Đề xuất ngựa <b>" + esc(e.getHorse().getHorseName()) + "</b> thi giải <b>" + esc(e.getCompetition().getCompetitionName())
                + "</b> sẽ hết hạn " + STAGE.get(e.getStatus()) + " lúc <b>" + fmt(e.getStageDeadline()) + "</b>. Quá hạn đề xuất sẽ mất suất.</p>";
        switch (e.getStatus()) {
            case CompetitionEntry.WAITING_TRAINER -> notifier.notify(e.getHorse().getAssignedTrainer(), subject, html);
            case CompetitionEntry.WAITING_VET_EXAM -> notifier.notifyRole("Veterinarian", subject, html);
            default -> { }
        }
        notifier.notify(e.getOwner(), subject, html);
    }

    private List<CompetitionEntry> entries(Integer competitionId, List<String> statuses) {
        return entryRepository.findByCompetition_CompetitionIdAndStatusInOrderByProposedAtAscEntryIdAsc(competitionId, statuses);
    }

    private static void inc(Map<String, Integer> r, String key) {
        r.merge(key, 1, Integer::sum);
    }
}
