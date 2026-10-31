package com.horsetrack.equinecare.modules.tournament.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import com.horsetrack.equinecare.modules.finance.entity.InvoiceLineItem;
import com.horsetrack.equinecare.modules.finance.repository.InvoiceRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.medical.repository.VaccinationRepository;
import com.horsetrack.equinecare.modules.tournament.dto.*;
import com.horsetrack.equinecare.modules.tournament.entity.CompetitionEntry;
import com.horsetrack.equinecare.modules.tournament.entity.ExternalCompetition;
import com.horsetrack.equinecare.modules.tournament.repository.CompetitionEntryRepository;
import com.horsetrack.equinecare.modules.tournament.repository.ExternalCompetitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static com.horsetrack.equinecare.modules.tournament.service.RaceNotifier.esc;
import static com.horsetrack.equinecare.modules.tournament.service.RaceNotifier.fmt;

/**
 * Đề xuất thi đấu: chủ ngựa đề xuất (tối đa 1 đề xuất còn hiệu lực / giải) → giữ chỗ theo thứ tự hoặc vào hàng chờ
 * → HLV xác nhận (2 ngày) → bác sĩ khám (3 ngày) → thanh toán lệ phí (2 ngày) → REGISTERED.
 * HLV từ chối phải chọn mã lý do; chủ ngựa khiếu nại lên Quản lý CLB (chấp nhận: về WAITING_VET_EXAM, giữ nguyên suất).
 */
@Service
@RequiredArgsConstructor
public class RaceProposalService {

    private final ExternalCompetitionRepository competitionRepository;
    private final CompetitionEntryRepository entryRepository;
    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;
    private final VaccinationRepository vaccinationRepository;
    private final HorseEligibilityService eligibility;
    private final RaceSlotAllocator slots;
    private final RaceMapper mapper;
    private final RaceNotifier notifier;

    public static String label(String status) {
        return RaceNotifier.label(status);
    }

    public RaceProposalDTO toDto(CompetitionEntry e) {
        return mapper.toDto(e);
    }

    // ---------- Xem ----------

    /** HLV: ngựa được giao; chủ ngựa: đề xuất của mình; bác sĩ / quản lý CLB: tất cả. Lọc theo status, complaintStatus, tournamentId */
    @Transactional(readOnly = true)
    public List<RaceProposalDTO> listProposals(String status, String complaintStatus, Integer tournamentId) {
        User user = currentUser();
        List<CompetitionEntry> list = switch (user.getRoleName()) {
            case "Trainer" -> entryRepository.findByHorse_AssignedTrainer_UserIdOrderByProposedAtDesc(user.getUserId());
            case "Horse Owner" -> entryRepository.findByOwner_UserIdOrderByProposedAtDesc(user.getUserId());
            default -> StringUtils.hasText(complaintStatus)
                    ? entryRepository.findByComplaintStatusOrderByComplaintAtAsc(complaintStatus.trim().toUpperCase())
                    : entryRepository.findAllByOrderByProposedAtDesc();
        };
        return list.stream()
                .filter(e -> !StringUtils.hasText(status) || status.trim().equalsIgnoreCase(e.getStatus()))
                .filter(e -> !StringUtils.hasText(complaintStatus) || complaintStatus.trim().equalsIgnoreCase(e.getComplaintStatus()))
                .filter(e -> tournamentId == null || tournamentId.equals(e.getCompetition().getCompetitionId()))
                .map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RaceProposalDTO getProposal(Integer id) {
        CompetitionEntry entry = findEntry(id);
        checkCanView(entry, currentUser());
        return mapper.toDto(entry);
    }

    // ---------- Chủ ngựa: đề xuất / hủy ----------

    @Transactional
    public RaceProposalDTO create(RaceProposalCreateDTO request) {
        User owner = currentUser();
        // Khóa ngựa rồi khóa giải: 2 đề xuất song song không cùng lấy 1 suất / không vượt giới hạn 1 đề xuất mỗi chủ ngựa
        Horse horse = horseRepository.findByIdForUpdate(request.getHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + request.getHorseId()));
        if (horse.getOwner() == null || !horse.getOwner().getUserId().equals(owner.getUserId())) {
            throw new AccessDeniedException("Bạn không phải chủ của ngựa " + horse.getHorseName());
        }
        ExternalCompetition c = competitionRepository.findByIdForUpdate(request.getTournamentId())
                .filter(x -> !ExternalCompetition.DRAFT.equals(x.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giải đấu với ID: " + request.getTournamentId()));
        if (!RaceMapper.isOpen(c)) {
            throw new IllegalArgumentException("Giải " + c.getCompetitionName() + " đã hết hạn đăng ký hoặc đã kết thúc.");
        }
        List<CompetitionEntry> mine = entryRepository.findByCompetition_CompetitionIdAndOwner_UserIdOrderByProposedAtDesc(c.getCompetitionId(), owner.getUserId());
        mine.stream().filter(e -> CompetitionEntry.ACTIVE.contains(e.getStatus())).findFirst().ifPresent(e -> {
            throw new IllegalArgumentException("Mỗi chủ ngựa chỉ có 1 đề xuất còn hiệu lực cho mỗi giải. Bạn đã có đề xuất " + RaceNotifier.code(e)
                    + " (ngựa " + e.getHorse().getHorseName() + ", " + label(e.getStatus()) + "); hãy hủy đề xuất đó nếu muốn chọn ngựa khác.");
        });
        if (mine.stream().anyMatch(e -> CompetitionEntry.COMPLAINT_PENDING.equals(e.getComplaintStatus()))) {
            throw new IllegalArgumentException("Bạn đang có khiếu nại chờ Quản lý CLB xử lý cho giải này, chưa thể đề xuất mới.");
        }
        HorseEligibilityDTO ev = eligibility.evaluate(horse, c);
        if (!ev.getEligible()) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " không đủ điều kiện thi giải " + c.getCompetitionName() + ": "
                    + String.join("; ", ev.getReasons()));
        }

        LocalDateTime now = LocalDateTime.now();
        CompetitionEntry entry = entryRepository.saveAndFlush(CompetitionEntry.builder()
                .competition(c)
                .horse(horse)
                .owner(owner)
                .status(CompetitionEntry.WAITLISTED)
                .proposedBy(owner)
                .proposedAt(now)
                .proposalNote(trimOrNull(request.getNote()))
                .formScore(ev.getFormScore())
                .deadlineReminded(false)
                .entryFeeCharged(c.getStandardEntryFee() != null ? c.getStandardEntryFee() : BigDecimal.ZERO)
                .transportFee(BigDecimal.ZERO)
                .prizeAwarded(BigDecimal.ZERO)
                .build());
        // Còn suất và không ai xếp hàng trước: giữ chỗ ngay; ngược lại vào cuối hàng chờ
        boolean queueAhead = slots.queueLength(c.getCompetitionId()) > 1;
        if (!queueAhead && slots.holding(c.getCompetitionId()) < c.getSlots()) {
            slots.hold(entry, now);
        } else {
            slots.promoteWaitlist(c, now);
        }
        notifier.audit("CREATE_RACE_PROPOSAL", null, entry, entry.getStatus().equals(CompetitionEntry.WAITLISTED) ? "vào hàng chờ" : "giữ chỗ");
        notifier.notify(owner, "[HorseTrack] Đã nhận đề xuất " + RaceNotifier.code(entry) + " cho ngựa " + horse.getHorseName(),
                CompetitionEntry.WAITLISTED.equals(entry.getStatus())
                        ? "<p>Giải <b>" + esc(c.getCompetitionName()) + "</b> đã hết suất chính thức, đề xuất của bạn đang ở <b>vị trí "
                                + slots.queuePosition(entry) + "</b> trong hàng chờ. Khi có suất trống hệ thống sẽ tự đôn lên và báo cho bạn.</p>"
                        : "<p>Đề xuất ngựa <b>" + esc(horse.getHorseName()) + "</b> thi giải <b>" + esc(c.getCompetitionName())
                                + "</b> đang giữ chỗ. HLV cần xác nhận trước <b>" + fmt(entry.getStageDeadline()) + "</b>.</p>");
        return mapper.toDto(entry);
    }

    @Transactional
    public RaceProposalDTO cancel(Integer id) {
        User owner = currentUser();
        CompetitionEntry e = findEntry(id);
        requireOwner(e, owner);
        competitionRepository.findByIdForUpdate(e.getCompetition().getCompetitionId());
        if (CompetitionEntry.REGISTERED.equals(e.getStatus())) {
            throw new IllegalArgumentException("Đề xuất đã thanh toán lệ phí và đăng ký thi đấu; vui lòng liên hệ Quản lý CLB để rút và xử lý hoàn tiền.");
        }
        if (!CompetitionEntry.WAITLISTED.equals(e.getStatus()) && !CompetitionEntry.IN_STAGE.contains(e.getStatus())) {
            throw new IllegalArgumentException("Chỉ hủy được đề xuất còn hiệu lực (hiện tại: " + label(e.getStatus()) + ").");
        }
        if (e.getInvoice() != null && RaceSlotAllocator.hasLiveTransaction(e.getInvoice())) {
            throw new IllegalArgumentException("Hóa đơn lệ phí " + e.getInvoice().getInvoiceCode() + " đang có giao dịch VNPay chờ xác minh, chưa hủy được.");
        }
        slots.close(e, CompetitionEntry.CANCELLED, "Chủ ngựa tự hủy đề xuất", "OWNER_CANCEL_RACE", LocalDateTime.now());
        return mapper.toDto(e);
    }

    // ---------- HLV xác nhận / từ chối ----------

    @Transactional
    public RaceProposalDTO trainerDecision(Integer id, TrainerDecisionDTO request) {
        User trainer = currentUser();
        CompetitionEntry e = findEntry(id);
        Horse h = e.getHorse();
        if (h.getAssignedTrainer() == null || !h.getAssignedTrainer().getUserId().equals(trainer.getUserId())) {
            throw new AccessDeniedException("Chỉ HLV phụ trách ngựa " + h.getHorseName() + " mới được xác nhận / từ chối đề xuất");
        }
        ExternalCompetition c = competitionRepository.findByIdForUpdate(e.getCompetition().getCompetitionId()).orElse(e.getCompetition());
        requireStatus(e, CompetitionEntry.WAITING_TRAINER, "xác nhận / từ chối");
        LocalDateTime now = LocalDateTime.now();
        if (e.getStageDeadline() != null && !now.isBefore(e.getStageDeadline())) {
            throw new IllegalArgumentException("Đã quá hạn HLV xác nhận (" + fmt(e.getStageDeadline()) + "), đề xuất sẽ bị hệ thống hủy.");
        }
        boolean approve = Boolean.TRUE.equals(request.getApprove());
        e.setTrainer(trainer);
        e.setTrainerApproved(approve);
        e.setTrainerDecidedAt(now);
        e.setTrainerNote(trimOrNull(request.getNote()));
        if (!approve) {
            String code = request.getReasonCode() == null ? null : request.getReasonCode().trim().toUpperCase();
            if (code == null || code.isEmpty()) {
                throw new IllegalArgumentException("Từ chối đề xuất bắt buộc chọn mã lý do (reasonCode): " + String.join(", ", RaceNotifier.REJECT_REASONS.keySet()) + ".");
            }
            if (!RaceNotifier.REJECT_REASONS.containsKey(code)) {
                throw new IllegalArgumentException("Mã lý do '" + request.getReasonCode() + "' không hợp lệ (" + String.join(", ", RaceNotifier.REJECT_REASONS.keySet()) + ").");
            }
            if ("OTHER".equals(code) && e.getTrainerNote() == null) {
                throw new IllegalArgumentException("Lý do Khác cần ghi rõ trong ghi chú (note).");
            }
            e.setTrainerReasonCode(code);
            String reason = "HLV " + trainer.getFullName() + " từ chối: " + RaceNotifier.REJECT_REASONS.get(code)
                    + (e.getTrainerNote() != null ? " - " + e.getTrainerNote() : "");
            slots.close(e, CompetitionEntry.TRAINER_REJECTED, reason, "TRAINER_REJECT_RACE", now,
                    "<p>Nếu không đồng ý, bạn có thể gửi <b>khiếu nại</b> lên Quản lý CLB trên HorseTrack trước khi hết hạn đăng ký ("
                            + fmt(c.registrationCloseAt()) + ").</p>");
            return mapper.toDto(e);
        }
        // Kiểm tra lại điều kiện ngựa trước khi qua chặng 2
        List<String> fail = eligibility.failures(h, c);
        if (!fail.isEmpty()) {
            slots.close(e, CompetitionEntry.INELIGIBLE, "Ngựa không còn đủ điều kiện khi HLV xác nhận: " + String.join("; ", fail), "RACE_AUTO_INELIGIBLE", now);
            return mapper.toDto(e);
        }
        e.setStatus(CompetitionEntry.WAITING_VET_EXAM);
        e.setStageDeadline(RaceSlotAllocator.deadline(c, now, slots.getVetDays()));
        e.setDeadlineReminded(false);
        entryRepository.save(e);
        notifier.audit("TRAINER_APPROVE_RACE", CompetitionEntry.WAITING_TRAINER, e, e.getTrainerNote());
        notifier.notify(e.getOwner(), "[HorseTrack] HLV đã xác nhận đề xuất " + RaceNotifier.code(e),
                "<p>HLV <b>" + esc(trainer.getFullName()) + "</b> đã xác nhận cho ngựa <b>" + esc(h.getHorseName()) + "</b> thi giải <b>"
                        + esc(c.getCompetitionName()) + "</b>. Bác sĩ sẽ khám tiền thi đấu trước <b>" + fmt(e.getStageDeadline()) + "</b>.</p>");
        notifier.notifyRole("Veterinarian", "[HorseTrack] Cần khám tiền thi đấu " + RaceNotifier.code(e),
                "<p>Ngựa <b>" + esc(h.getHorseName()) + "</b> cần khám tiền thi đấu cho giải <b>" + esc(c.getCompetitionName())
                        + "</b> (ngày " + c.getStartDate() + "). Hạn khám: <b>" + fmt(e.getStageDeadline()) + "</b>.</p>");
        return mapper.toDto(e);
    }

    // ---------- Bác sĩ khám (gọi từ PreRaceExamService sau khi đã ghi kết luận) ----------

    @Transactional
    public void afterVetExam(CompetitionEntry e) {
        LocalDateTime now = LocalDateTime.now();
        ExternalCompetition c = competitionRepository.findByIdForUpdate(e.getCompetition().getCompetitionId()).orElse(e.getCompetition());
        if (!Boolean.TRUE.equals(e.getVetPassed())) {
            slots.close(e, CompetitionEntry.VET_FAILED, "Bác sĩ " + e.getVet().getFullName() + " kết luận khám không đạt"
                    + (e.getVetNote() != null ? ": " + e.getVetNote() : ""), "PRERACE_EXAM_FAIL", now);
            return;
        }
        List<String> fail = eligibility.failures(e.getHorse(), c, Set.of("HAS_TRAINER"));
        if (!fail.isEmpty()) {
            slots.close(e, CompetitionEntry.INELIGIBLE, "Ngựa không còn đủ điều kiện sau khi khám: " + String.join("; ", fail), "RACE_AUTO_INELIGIBLE", now);
            return;
        }
        BigDecimal fee = e.getEntryFeeCharged() == null ? BigDecimal.ZERO : e.getEntryFeeCharged();
        if (fee.signum() <= 0) {
            register(e, now, "Khám đạt, giải không thu lệ phí");
            notifier.audit("PRERACE_EXAM_PASS", CompetitionEntry.WAITING_VET_EXAM, e, "không thu lệ phí -> REGISTERED");
            return;
        }
        e.setStatus(CompetitionEntry.WAITING_PAYMENT);
        e.setStageDeadline(RaceSlotAllocator.deadline(c, now, slots.getPaymentDays()));
        e.setDeadlineReminded(false);
        e.setInvoice(issueFeeInvoice(e, fee, now));
        entryRepository.save(e);
        notifier.audit("PRERACE_EXAM_PASS", CompetitionEntry.WAITING_VET_EXAM, e, "hóa đơn " + e.getInvoice().getInvoiceCode());
        notifier.notify(e.getOwner(), "[HorseTrack] Ngựa " + e.getHorse().getHorseName() + " khám đạt - thanh toán lệ phí giải",
                "<p>Ngựa <b>" + esc(e.getHorse().getHorseName()) + "</b> đã khám tiền thi đấu đạt cho giải <b>" + esc(c.getCompetitionName()) + "</b>.</p>"
                        + "<p>Hóa đơn lệ phí <b>" + e.getInvoice().getInvoiceCode() + "</b>: <b>" + TournamentService.money(fee) + "</b>. "
                        + "Vui lòng thanh toán trước <b>" + fmt(e.getStageDeadline()) + "</b>; quá hạn đề xuất sẽ mất suất.</p>");
    }

    // ---------- Thanh toán (gọi từ PaymentService khi hóa đơn chuyển PAID) ----------

    @Transactional
    public void onInvoicePaid(Invoice invoice) {
        CompetitionEntry e = entryRepository.findFirstByInvoice_InvoiceId(invoice.getInvoiceId()).orElse(null);
        if (e == null) {
            return;
        }
        if (!CompetitionEntry.WAITING_PAYMENT.equals(e.getStatus())) {
            // Thanh toán về sau khi đề xuất đã mất suất: báo Quản lý CLB đối soát hoàn tiền
            notifier.audit("RACE_PAID_AFTER_CLOSE", e.getStatus(), e, "hóa đơn " + invoice.getInvoiceCode() + " đã thanh toán, cần hoàn tiền");
            notifier.notifyRole("Club Manager", "[HorseTrack] Cần hoàn lệ phí giải " + invoice.getInvoiceCode(),
                    "<p>Hóa đơn lệ phí <b>" + invoice.getInvoiceCode() + "</b> của đề xuất <b>" + RaceNotifier.code(e) + "</b> được thanh toán khi đề xuất đã ở trạng thái <b>"
                            + esc(label(e.getStatus())) + "</b>. Vui lòng đối soát và hoàn tiền cho chủ ngựa.</p>", "/manager/invoices");
            return;
        }
        register(e, LocalDateTime.now(), "Đã thanh toán hóa đơn " + invoice.getInvoiceCode());
    }

    // ---------- Khiếu nại ----------

    @Transactional
    public RaceProposalDTO complaint(Integer id, ComplaintDTO request) {
        User owner = currentUser();
        CompetitionEntry e = findEntry(id);
        requireOwner(e, owner);
        requireStatus(e, CompetitionEntry.TRAINER_REJECTED, "khiếu nại");
        if (e.getComplaintStatus() != null) {
            throw new IllegalArgumentException("Đề xuất này đã được khiếu nại (" + e.getComplaintStatus() + ").");
        }
        ExternalCompetition c = competitionRepository.findByIdForUpdate(e.getCompetition().getCompetitionId()).orElse(e.getCompetition());
        if (!RaceMapper.isOpen(c)) {
            throw new IllegalArgumentException("Giải đã hết hạn đăng ký, không thể khiếu nại.");
        }
        requireNoOtherActive(e);
        e.setComplaintStatus(CompetitionEntry.COMPLAINT_PENDING);
        e.setComplaintNote(request.getNote().trim());
        e.setComplaintAt(LocalDateTime.now());
        entryRepository.save(e);
        notifier.audit("RACE_COMPLAINT", e.getStatus(), e, "khiếu nại: " + e.getComplaintNote());
        notifier.notifyRole("Club Manager", "[HorseTrack] Khiếu nại đề xuất thi đấu " + RaceNotifier.code(e),
                "<p>Chủ ngựa <b>" + esc(owner.getFullName()) + "</b> khiếu nại việc HLV từ chối cho ngựa <b>" + esc(e.getHorse().getHorseName())
                        + "</b> thi giải <b>" + esc(c.getCompetitionName()) + "</b>.</p><p>Lý do HLV: " + esc(e.getClosedReason()) + "</p>"
                        + "<p>Nội dung khiếu nại: " + esc(e.getComplaintNote()) + "</p>");
        return mapper.toDto(e);
    }

    @Transactional
    public RaceProposalDTO complaintDecision(Integer id, ComplaintDecisionDTO request) {
        User manager = currentUser();
        CompetitionEntry e = findEntry(id);
        if (!CompetitionEntry.COMPLAINT_PENDING.equals(e.getComplaintStatus())) {
            throw new IllegalArgumentException("Đề xuất " + RaceNotifier.code(e) + " không có khiếu nại đang chờ xử lý.");
        }
        ExternalCompetition c = competitionRepository.findByIdForUpdate(e.getCompetition().getCompetitionId()).orElse(e.getCompetition());
        LocalDateTime now = LocalDateTime.now();
        boolean accept = Boolean.TRUE.equals(request.getAccept());
        e.setComplaintDecidedBy(manager);
        e.setComplaintDecidedAt(now);
        e.setComplaintDecisionNote(trimOrNull(request.getNote()));
        if (!accept) {
            e.setComplaintStatus(CompetitionEntry.COMPLAINT_REJECTED);
            entryRepository.save(e);
            notifier.audit("RACE_COMPLAINT_REJECTED", e.getStatus(), e, e.getComplaintDecisionNote());
            notifier.notify(e.getOwner(), "[HorseTrack] Khiếu nại đề xuất " + RaceNotifier.code(e) + " không được chấp nhận",
                    "<p>Quản lý CLB đã xem xét và <b>không chấp nhận</b> khiếu nại cho ngựa <b>" + esc(e.getHorse().getHorseName()) + "</b> (giải <b>"
                            + esc(c.getCompetitionName()) + "</b>).</p>" + (e.getComplaintDecisionNote() != null ? "<p>Ghi chú: " + esc(e.getComplaintDecisionNote()) + "</p>" : ""));
            return mapper.toDto(e);
        }
        if (!RaceMapper.isOpen(c)) {
            throw new IllegalArgumentException("Giải đã hết hạn đăng ký nên không thể khôi phục suất; chỉ có thể bác khiếu nại.");
        }
        requireNoOtherActive(e);
        List<String> fail = eligibility.failures(e.getHorse(), c);
        if (!fail.isEmpty()) {
            throw new IllegalArgumentException("Ngựa hiện không đủ điều kiện nên không thể chấp nhận khiếu nại: " + String.join("; ", fail));
        }
        String before = e.getStatus();
        e.setComplaintStatus(CompetitionEntry.COMPLAINT_ACCEPTED);
        // Giữ nguyên suất: quay lại chặng khám, không xếp lại hàng chờ (có thể tạm vượt số suất nếu suất đã được đôn cho người khác)
        e.setStatus(CompetitionEntry.WAITING_VET_EXAM);
        e.setStageDeadline(RaceSlotAllocator.deadline(c, now, slots.getVetDays()));
        e.setDeadlineReminded(false);
        e.setClosedAt(null);
        e.setClosedReason(null);
        entryRepository.save(e);
        notifier.audit("RACE_COMPLAINT_ACCEPTED", before, e, "giữ nguyên suất | " + (e.getComplaintDecisionNote() == null ? "" : e.getComplaintDecisionNote()));
        notifier.notify(e.getOwner(), "[HorseTrack] Khiếu nại đề xuất " + RaceNotifier.code(e) + " được chấp nhận",
                "<p>Quản lý CLB đã chấp nhận khiếu nại. Ngựa <b>" + esc(e.getHorse().getHorseName()) + "</b> giữ nguyên suất ở giải <b>"
                        + esc(c.getCompetitionName()) + "</b> và chuyển sang chờ bác sĩ khám (hạn " + fmt(e.getStageDeadline()) + ").</p>");
        notifier.notify(e.getHorse().getAssignedTrainer(), "[HorseTrack] Quản lý CLB chấp nhận khiếu nại đề xuất " + RaceNotifier.code(e),
                "<p>Đề xuất cho ngựa <b>" + esc(e.getHorse().getHorseName()) + "</b> thi giải <b>" + esc(c.getCompetitionName())
                        + "</b> mà bạn đã từ chối được Quản lý CLB chấp nhận khiếu nại và chuyển sang chờ bác sĩ khám.</p>");
        notifier.notifyRole("Veterinarian", "[HorseTrack] Cần khám tiền thi đấu " + RaceNotifier.code(e),
                "<p>Ngựa <b>" + esc(e.getHorse().getHorseName()) + "</b> cần khám tiền thi đấu cho giải <b>" + esc(c.getCompetitionName())
                        + "</b>. Hạn khám: <b>" + fmt(e.getStageDeadline()) + "</b>.</p>");
        return mapper.toDto(e);
    }

    // ---------- Hồ sơ đăng ký ----------

    @Transactional(readOnly = true)
    public RaceDossierDTO dossier(Integer id) {
        CompetitionEntry e = findEntry(id);
        checkCanView(e, currentUser());
        Horse h = e.getHorse();
        User trainer = e.getTrainer() != null ? e.getTrainer() : h.getAssignedTrainer();
        Invoice inv = e.getInvoice();
        return RaceDossierDTO.builder()
                .proposal(mapper.toDto(e))
                .horse(RaceDossierDTO.HorseInfo.builder()
                        .horseId(h.getHorseId())
                        .horseCode(h.getHorseCode())
                        .horseName(h.getHorseName())
                        .dob(h.getDob())
                        .ageYears(h.getDob() != null ? Period.between(h.getDob(), e.getCompetition().getStartDate()).getYears() : null)
                        .gender(h.getGender())
                        .breed(h.getBreed())
                        .color(h.getColor())
                        .pedigree(h.getPedigree())
                        .weightKg(h.getWeightKg())
                        .heightCm(h.getHeightCm())
                        .healthStatus(h.getHealthStatus())
                        .stallCode(h.getStall() != null ? h.getStall().getStallCode() : null)
                        .build())
                .owner(person(e.getOwner() != null ? e.getOwner() : h.getOwner()))
                .trainer(person(trainer))
                .vaccinations(vaccinationRepository.findByHorse_HorseIdOrderByDueDateAscVaccinationIdAsc(h.getHorseId()).stream()
                        .map(v -> RaceDossierDTO.Vaccine.builder()
                                .code(v.getVaccinationCode())
                                .vaccineName(v.getVaccineName())
                                .kind(v.getKind())
                                .dueDate(v.getDueDate())
                                .doneAt(v.getDoneAt())
                                .status(v.getStatus())
                                .lotNo(v.getLotNo())
                                .build())
                        .toList())
                .vetExam(e.getVetPassed() == null ? null : RaceDossierDTO.VetExam.builder()
                        .vetName(e.getVet() != null ? e.getVet().getFullName() : null)
                        .passed(e.getVetPassed())
                        .checkedAt(e.getVetCheckedAt())
                        .note(e.getVetNote())
                        .build())
                .payment(inv == null ? null : RaceDossierDTO.Payment.builder()
                        .invoiceCode(inv.getInvoiceCode())
                        .amount(inv.getFinalPayableAmount())
                        .status(inv.getPaymentStatus())
                        .paidAt(e.getPaidAt())
                        .build())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ---------- Hỗ trợ ----------

    private void register(CompetitionEntry e, LocalDateTime now, String note) {
        String before = e.getStatus();
        e.setStatus(CompetitionEntry.REGISTERED);
        e.setPaidAt(now);
        e.setStageDeadline(null);
        entryRepository.save(e);
        notifier.audit("RACE_REGISTERED", before, e, note);
        String html = "<p>Ngựa <b>" + esc(e.getHorse().getHorseName()) + "</b> đã <b>đăng ký thi đấu</b> giải <b>" + esc(e.getCompetition().getCompetitionName())
                + "</b> ngày " + e.getCompetition().getStartDate() + " tại " + esc(e.getCompetition().getLocation()) + ".</p>"
                + "<p>Hồ sơ đăng ký được tổng hợp trên HorseTrack; việc nộp hồ sơ giấy và thi đấu diễn ra offline.</p>";
        notifier.notify(e.getOwner(), "[HorseTrack] Ngựa " + e.getHorse().getHorseName() + " đã đăng ký thi đấu", html);
        notifier.notify(e.getHorse().getAssignedTrainer(), "[HorseTrack] Ngựa " + e.getHorse().getHorseName() + " đã đăng ký thi đấu",
                html + "<p>Lưu ý: trong 7 ngày trước ngày thi không giao giáo án / buổi tập cường độ Cao cho ngựa này.</p>");
    }

    // Hóa đơn lệ phí đăng ký giải: TD-<entry_id 6 số>, hạn = hạn chặng thanh toán
    private Invoice issueFeeInvoice(CompetitionEntry e, BigDecimal fee, LocalDateTime now) {
        String base = String.format("TD-%06d", e.getEntryId());
        String code = base;
        for (int n = 2; invoiceRepository.existsByInvoiceCode(code); n++) {
            code = base + "-" + n;
        }
        String desc = "Lệ phí đăng ký giải " + e.getCompetition().getCompetitionName() + " - ngựa " + e.getHorse().getHorseName();
        InvoiceLineItem line = InvoiceLineItem.builder()
                .expenseCategory("COMPETITION")
                .referenceId(e.getEntryId())
                .itemDescription(desc.length() > 255 ? desc.substring(0, 255) : desc)
                .unitPrice(fee)
                .quantity(BigDecimal.ONE)
                .serviceDate(e.getCompetition().getStartDate().atTime(8, 0))
                .build();
        Invoice invoice = Invoice.builder()
                .invoiceCode(code)
                .horse(e.getHorse())
                .owner(e.getOwner() != null ? e.getOwner() : e.getHorse().getOwner())
                .billingMonth(YearMonth.from(now).toString())
                .subtotalAmount(fee)
                .prizeDeductionAmount(BigDecimal.ZERO)
                .finalPayableAmount(fee)
                .paymentStatus(Invoice.UNPAID)
                .issuedAt(now)
                .dueDate(e.getStageDeadline().toLocalDate())
                .build();
        line.setInvoice(invoice);
        invoice.getLines().add(line);
        return invoiceRepository.save(invoice);
    }

    // Chủ ngựa / ngựa không có đề xuất còn hiệu lực khác trong cùng giải (trước khi khôi phục / khiếu nại)
    private void requireNoOtherActive(CompetitionEntry e) {
        Integer cid = e.getCompetition().getCompetitionId();
        for (CompetitionEntry o : entryRepository.findByCompetition_CompetitionIdAndStatusInOrderByProposedAtAscEntryIdAsc(cid, CompetitionEntry.ACTIVE)) {
            if (o.getEntryId().equals(e.getEntryId())) continue;
            boolean sameOwner = o.getOwner() != null && e.getOwner() != null && o.getOwner().getUserId().equals(e.getOwner().getUserId());
            if (sameOwner || o.getHorse().getHorseId().equals(e.getHorse().getHorseId())) {
                throw new IllegalArgumentException("Chủ ngựa đã có đề xuất khác còn hiệu lực cho giải này (" + RaceNotifier.code(o) + ", ngựa "
                        + o.getHorse().getHorseName() + "). Hãy hủy đề xuất đó trước.");
            }
        }
    }

    private void checkCanView(CompetitionEntry e, User user) {
        Horse h = e.getHorse();
        switch (user.getRoleName()) {
            case "Trainer" -> {
                if (h.getAssignedTrainer() == null || !h.getAssignedTrainer().getUserId().equals(user.getUserId()))
                    throw new AccessDeniedException("Bạn không phụ trách ngựa " + h.getHorseName());
            }
            case "Horse Owner" -> requireOwner(e, user);
            default -> { }
        }
    }

    private static void requireOwner(CompetitionEntry e, User user) {
        Integer ownerId = e.getOwner() != null ? e.getOwner().getUserId() : e.getHorse().getOwner() != null ? e.getHorse().getOwner().getUserId() : null;
        if (ownerId == null || !ownerId.equals(user.getUserId())) {
            throw new AccessDeniedException("Bạn không phải chủ của đề xuất " + RaceNotifier.code(e));
        }
    }

    private static void requireStatus(CompetitionEntry e, String status, String action) {
        if (!status.equals(e.getStatus())) {
            throw new IllegalArgumentException("Chỉ " + action + " khi đề xuất đang ở trạng thái '" + label(status) + "' (hiện tại: " + label(e.getStatus()) + ").");
        }
    }

    private static RaceDossierDTO.Person person(User u) {
        return u == null ? null : RaceDossierDTO.Person.builder().userId(u.getUserId()).fullName(u.getFullName()).phone(u.getPhone()).email(u.getEmail()).build();
    }

    private CompetitionEntry findEntry(Integer id) {
        return entryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề xuất thi đấu với ID: " + id));
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }

    private static String trimOrNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
