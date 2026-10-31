package com.horsetrack.equinecare.modules.tournament.service;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import com.horsetrack.equinecare.modules.finance.entity.PaymentTransaction;
import com.horsetrack.equinecare.modules.finance.repository.InvoiceRepository;
import com.horsetrack.equinecare.modules.tournament.entity.CompetitionEntry;
import com.horsetrack.equinecare.modules.tournament.entity.ExternalCompetition;
import com.horsetrack.equinecare.modules.tournament.repository.CompetitionEntryRepository;
import com.horsetrack.equinecare.modules.tournament.repository.ExternalCompetitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.horsetrack.equinecare.modules.tournament.service.RaceNotifier.esc;
import static com.horsetrack.equinecare.modules.tournament.service.RaceNotifier.fmt;

/**
 * Suất chính thức và hàng chờ của giải: giữ chỗ theo thứ tự đề xuất, tính hạn chặng (không vượt hạn đăng ký chung),
 * đóng đề xuất khi mất suất và tự đôn người đứng đầu hàng chờ lên.
 */
@Component
@RequiredArgsConstructor
public class RaceSlotAllocator {

    // Trạng thái mất suất do hệ thống tự hủy (ghi nhật ký "Hệ thống tự hủy")
    private static final Set<String> SYSTEM_CLOSE = Set.of(CompetitionEntry.TRAINER_EXPIRED, CompetitionEntry.VET_EXPIRED,
            CompetitionEntry.PAYMENT_EXPIRED, CompetitionEntry.INELIGIBLE, CompetitionEntry.NO_SLOT);

    @Value("${app.tournament.trainer-days:2}")
    private int trainerDays;
    @Value("${app.tournament.vet-days:3}")
    private int vetDays;
    @Value("${app.tournament.payment-days:2}")
    private int paymentDays;

    private final ExternalCompetitionRepository competitionRepository;
    private final CompetitionEntryRepository entryRepository;
    private final HorseEligibilityService eligibility;
    private final InvoiceRepository invoiceRepository;
    private final RaceNotifier notifier;

    public int getTrainerDays() {
        return trainerDays;
    }

    public int getVetDays() {
        return vetDays;
    }

    public int getPaymentDays() {
        return paymentDays;
    }

    /** Hạn chặng = from + days, nhưng không vượt quá hạn đăng ký chung của giải */
    public static LocalDateTime deadline(ExternalCompetition c, LocalDateTime from, int days) {
        LocalDateTime d = from.plusDays(days);
        LocalDateTime close = c.registrationCloseAt();
        return d.isAfter(close) ? close : d;
    }

    public long holding(Integer competitionId) {
        return entryRepository.countByCompetition_CompetitionIdAndStatusIn(competitionId, CompetitionEntry.HOLDING);
    }

    public long queueLength(Integer competitionId) {
        return entryRepository.countByCompetition_CompetitionIdAndStatusIn(competitionId, List.of(CompetitionEntry.WAITLISTED));
    }

    /** Vị trí trong hàng chờ (1 = đứng đầu), null nếu không ở hàng chờ */
    public Integer queuePosition(CompetitionEntry e) {
        if (!CompetitionEntry.WAITLISTED.equals(e.getStatus())) {
            return null;
        }
        List<CompetitionEntry> queue = entryRepository.findByCompetition_CompetitionIdAndStatusInOrderByProposedAtAscEntryIdAsc(
                e.getCompetition().getCompetitionId(), List.of(CompetitionEntry.WAITLISTED));
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getEntryId().equals(e.getEntryId())) {
                return i + 1;
            }
        }
        return null;
    }

    /** Giữ chỗ: chặng 1 - chờ HLV xác nhận trong trainerDays ngày */
    public void hold(CompetitionEntry e, LocalDateTime now) {
        e.setStatus(CompetitionEntry.WAITING_TRAINER);
        e.setSlotHeldAt(now);
        e.setStageDeadline(deadline(e.getCompetition(), now, trainerDays));
        e.setDeadlineReminded(false);
        entryRepository.save(e);
        User trainer = e.getHorse().getAssignedTrainer();
        notifier.notify(trainer, "[HorseTrack] Cần xác nhận đề xuất thi đấu " + RaceNotifier.code(e),
                "<p>Chủ ngựa đề xuất ngựa <b>" + esc(e.getHorse().getHorseName()) + "</b> thi giải <b>" + esc(e.getCompetition().getCompetitionName())
                        + "</b> (ngày " + e.getCompetition().getStartDate() + "). Đề xuất đang giữ chỗ.</p>"
                        + "<p>Vui lòng xác nhận hoặc từ chối trước <b>" + fmt(e.getStageDeadline()) + "</b>; quá hạn đề xuất sẽ mất suất.</p>");
    }

    /**
     * Đóng đề xuất (mất suất / hủy / không đủ điều kiện / hết suất): hủy hóa đơn lệ phí chưa thanh toán, ghi nhật ký,
     * báo chủ ngựa; nếu đề xuất đang giữ suất thì đôn hàng chờ.
     */
    @Transactional
    public void close(CompetitionEntry e, String status, String reason, String action, LocalDateTime now) {
        close(e, status, reason, action, now, null);
    }

    /** extraHtml: đoạn thông báo thêm cho chủ ngựa (VD: quyền khiếu nại khi HLV từ chối) */
    @Transactional
    public void close(CompetitionEntry e, String status, String reason, String action, LocalDateTime now, String extraHtml) {
        String before = e.getStatus();
        boolean wasHolding = CompetitionEntry.HOLDING.contains(before);
        boolean system = SYSTEM_CLOSE.contains(status);
        cancelInvoice(e, reason);
        e.setStatus(status);
        e.setClosedAt(now);
        e.setClosedReason(truncate(reason));
        e.setStageDeadline(null);
        entryRepository.save(e);
        notifier.audit(action, before, e, (system ? "Hệ thống tự hủy: " : "") + reason);
        String again = CompetitionEntry.NO_SLOT.equals(status) ? ""
                : "<p>Bạn có thể đề xuất lại nếu giải còn hạn đăng ký; đề xuất mới sẽ xếp vào cuối hàng.</p>";
        notifier.notify(e.getOwner(), "[HorseTrack] Đề xuất " + RaceNotifier.code(e) + ": " + RaceNotifier.label(status),
                "<p>Đề xuất ngựa <b>" + esc(e.getHorse().getHorseName()) + "</b> thi giải <b>" + esc(e.getCompetition().getCompetitionName())
                        + "</b> chuyển sang <b>" + esc(RaceNotifier.label(status)) + "</b>.</p><p>Lý do: " + esc(reason) + "</p>"
                        + (extraHtml == null ? "" : extraHtml) + again);
        if (wasHolding) {
            promoteWaitlist(e.getCompetition(), now);
        }
    }

    /** Đôn hàng chờ cho đến khi đủ suất (chỉ khi giải đang công bố và chưa hết hạn đăng ký). Trả về số đề xuất được đôn. */
    @Transactional
    public int promoteWaitlist(ExternalCompetition competition, LocalDateTime now) {
        ExternalCompetition c = competitionRepository.findByIdForUpdate(competition.getCompetitionId()).orElse(competition);
        if (!ExternalCompetition.PUBLISHED.equals(c.getStatus()) || Boolean.TRUE.equals(c.getIsFinished()) || !now.isBefore(c.registrationCloseAt())) {
            return 0;
        }
        long held = holding(c.getCompetitionId());
        int promoted = 0;
        for (CompetitionEntry w : entryRepository.findByCompetition_CompetitionIdAndStatusInOrderByProposedAtAscEntryIdAsc(
                c.getCompetitionId(), List.of(CompetitionEntry.WAITLISTED))) {
            if (held >= c.getSlots()) {
                break;
            }
            List<String> fail = eligibility.failures(w.getHorse(), c);
            if (!fail.isEmpty()) {
                close(w, CompetitionEntry.INELIGIBLE, "Ngựa không còn đủ điều kiện khi đến lượt lên suất: " + String.join("; ", fail),
                        "RACE_AUTO_INELIGIBLE", now);
                continue;
            }
            hold(w, now);
            notifier.audit("RACE_PROMOTE_FROM_WAITLIST", CompetitionEntry.WAITLISTED, w, "Hệ thống đôn từ hàng chờ lên giữ chỗ");
            notifier.notify(w.getOwner(), "[HorseTrack] Ngựa " + w.getHorse().getHorseName() + " đã lên suất chính thức",
                    "<p>Đề xuất <b>" + RaceNotifier.code(w) + "</b> cho giải <b>" + esc(c.getCompetitionName()) + "</b> đã được đôn từ hàng chờ lên giữ chỗ.</p>"
                            + "<p>HLV cần xác nhận trước <b>" + fmt(w.getStageDeadline()) + "</b>, sau đó bác sĩ khám và bạn thanh toán lệ phí.</p>");
            held++;
            promoted++;
        }
        return promoted;
    }

    /** Hủy hóa đơn lệ phí còn mở (không hủy khi đã có giao dịch thành công / đang chờ xác minh) */
    public void cancelInvoice(CompetitionEntry e, String reason) {
        Invoice inv = e.getInvoice();
        if (inv == null || !inv.isOpen() || hasLiveTransaction(inv)) {
            return;
        }
        inv.setPaymentStatus(Invoice.CANCELLED);
        invoiceRepository.save(inv);
    }

    public static boolean hasLiveTransaction(Invoice inv) {
        return inv.getTransactions().stream().anyMatch(t -> PaymentTransaction.VERIFIED.equals(t.getPaymentStatus())
                || PaymentTransaction.PENDING_VERIFICATION.equals(t.getPaymentStatus()));
    }

    static String truncate(String s) {
        return s == null ? null : s.length() > 500 ? s.substring(0, 500) : s;
    }
}
