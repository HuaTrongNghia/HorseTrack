package com.horsetrack.equinecare.modules.tournament.service;

import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import com.horsetrack.equinecare.modules.finance.repository.InvoiceRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.tournament.entity.CompetitionEntry;
import com.horsetrack.equinecare.modules.tournament.entity.ExternalCompetition;
import com.horsetrack.equinecare.modules.tournament.repository.CompetitionEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.horsetrack.equinecare.modules.tournament.service.RaceNotifier.esc;

/**
 * Ngựa bị khóa huấn luyện (chấn thương / chỉ định y tế): hủy mọi đề xuất thi đấu còn hiệu lực của ngựa ở các giải chưa thi,
 * kể cả đề xuất đã thanh toán (REGISTERED) → INELIGIBLE. Lệ phí đã trả được ghi hoàn (hóa đơn REFUNDED) và báo Quản lý CLB chuyển tiền;
 * suất trống được đôn cho người đầu hàng chờ (còn hạn đăng ký), người được đôn nhận thông báo lên suất.
 */
@Component
@RequiredArgsConstructor
public class RaceInjuryWithdrawal {

    private final CompetitionEntryRepository entryRepository;
    private final InvoiceRepository invoiceRepository;
    private final RaceSlotAllocator slots;
    private final RaceNotifier notifier;
    private final AuditTrail auditTrail;

    /** Gọi trong transaction khóa huấn luyện. Trả về số đề xuất bị hủy. */
    @Transactional
    public int onHorseLocked(Horse horse, String reason) {
        String why = "Ngựa bị khóa huấn luyện" + (StringUtils.hasText(reason) ? ": " + reason.trim() : "");
        LocalDateTime now = LocalDateTime.now();
        int n = 0;
        for (CompetitionEntry e : entryRepository.findByHorse_HorseIdOrderByProposedAtDesc(horse.getHorseId())) {
            ExternalCompetition c = e.getCompetition();
            // Giải đã thi xong (chờ nhập kết quả) hoặc đề xuất đã đóng thì giữ nguyên
            if (!CompetitionEntry.ACTIVE.contains(e.getStatus()) || !ExternalCompetition.PUBLISHED.equals(c.getStatus()) || c.raceEnded()) {
                continue;
            }
            boolean registered = CompetitionEntry.REGISTERED.equals(e.getStatus());
            String refundHtml = refund(e, why);
            slots.close(e, CompetitionEntry.INELIGIBLE, why, "RACE_INJURY_WITHDRAW", now, refundHtml);
            if (registered) {
                notifier.notify(horse.getAssignedTrainer(), "[HorseTrack] Hủy đăng ký thi đấu của ngựa " + horse.getHorseName(),
                        "<p>Ngựa <b>" + esc(horse.getHorseName()) + "</b> bị khóa huấn luyện nên đã bị hủy đăng ký giải <b>" + esc(c.getCompetitionName())
                                + "</b> ngày " + c.getStartDate() + " (đề xuất " + RaceNotifier.code(e) + ").</p><p>Lý do: " + esc(why) + "</p>");
            }
            n++;
        }
        return n;
    }

    // Lệ phí đã thanh toán: hóa đơn chuyển REFUNDED, báo Quản lý CLB chuyển tiền; trả đoạn thông báo hoàn tiền cho chủ ngựa
    private String refund(CompetitionEntry e, String why) {
        Invoice inv = e.getInvoice();
        if (inv == null || !Invoice.PAID.equals(inv.getPaymentStatus())) {
            return null;
        }
        BigDecimal amount = inv.getFinalPayableAmount() == null ? BigDecimal.ZERO : inv.getFinalPayableAmount();
        inv.setPaymentStatus(Invoice.REFUNDED);
        invoiceRepository.save(inv);
        auditTrail.record("REFUND_INVOICE", "Invoices", "payment_status=PAID",
                "payment_status=REFUNDED | " + inv.getInvoiceCode() + " | lệ phí giải, đề xuất " + RaceNotifier.code(e) + " | " + why);
        notifier.notifyRole("Club Manager", "[HorseTrack] Hoàn lệ phí giải " + inv.getInvoiceCode(),
                "<p>Đề xuất <b>" + RaceNotifier.code(e) + "</b> (ngựa <b>" + esc(e.getHorse().getHorseName()) + "</b>, giải <b>"
                        + esc(e.getCompetition().getCompetitionName()) + "</b>) bị hủy do ngựa bị khóa huấn luyện sau khi đã thanh toán lệ phí.</p>"
                        + "<p>Hóa đơn <b>" + inv.getInvoiceCode() + "</b> đã chuyển <b>Đã hoàn tiền</b>. Vui lòng chuyển trả <b>"
                        + TournamentService.money(amount) + "</b> cho chủ ngựa " + esc(e.getOwner() != null ? e.getOwner().getFullName() : "") + ".</p>",
                "/manager/invoices");
        return "<p>Lệ phí đã thanh toán (hóa đơn <b>" + inv.getInvoiceCode() + "</b>, <b>" + TournamentService.money(amount)
                + "</b>) sẽ được CLB <b>hoàn lại toàn bộ</b>.</p>";
    }
}
