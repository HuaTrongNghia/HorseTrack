package com.horsetrack.equinecare.modules.tournament.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.horse.service.HorseAccessGuard;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import com.horsetrack.equinecare.modules.tournament.dto.*;
import com.horsetrack.equinecare.modules.tournament.entity.CompetitionEntry;
import com.horsetrack.equinecare.modules.tournament.entity.ExternalCompetition;
import com.horsetrack.equinecare.modules.tournament.repository.CompetitionEntryRepository;
import com.horsetrack.equinecare.modules.tournament.repository.ExternalCompetitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Giải đấu và luồng đề xuất thi đấu (HLV → chủ ngựa → bác sĩ khám tiền thi đấu → gửi BTC → kết quả) */
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceProposalService {

    public static final Map<String, String> STATUS_LABELS = Map.of(
            CompetitionEntry.WAITING_OWNER, "Chờ chủ ngựa",
            CompetitionEntry.OWNER_DECLINED, "Chủ ngựa từ chối",
            CompetitionEntry.WAITING_VET, "Chờ bác sĩ khám",
            CompetitionEntry.VET_FAILED, "Khám không đạt",
            CompetitionEntry.READY_TO_SUBMIT, "Sẵn sàng gửi BTC",
            CompetitionEntry.SUBMITTED, "Đã gửi BTC",
            CompetitionEntry.ACCEPTED, "BTC đã nhận",
            CompetitionEntry.REJECTED, "BTC từ chối",
            CompetitionEntry.RESULT_RECORDED, "Đã có kết quả",
            "WITHDRAWN", "Đã rút");

    private final ExternalCompetitionRepository competitionRepository;
    private final CompetitionEntryRepository entryRepository;
    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final HorseAccessGuard horseAccessGuard;
    private final EmailService emailService;
    private final AuditTrail auditTrail;

    // ---------- Giải đấu ----------

    @Transactional(readOnly = true)
    public List<TournamentDTO> listTournaments(boolean openOnly) {
        List<ExternalCompetition> list = openOnly ? competitionRepository.findByIsFinishedFalseOrderByStartDateAsc()
                : competitionRepository.findAllByOrderByStartDateDesc();
        return list.stream().map(RaceProposalService::toDto).filter(t -> !openOnly || Boolean.TRUE.equals(t.getOpenForProposal())).toList();
    }

    @Transactional
    public TournamentDTO createTournament(TournamentCreateDTO request) {
        if (request.getRegistrationDeadline() != null && request.getRegistrationDeadline().isAfter(request.getRaceDate())) {
            throw new IllegalArgumentException("Hạn đăng ký phải trước hoặc bằng ngày đua.");
        }
        String code = StringUtils.hasText(request.getCode()) ? request.getCode().trim() : null;
        if (code != null && competitionRepository.existsByCompetitionCode(code)) {
            throw new IllegalArgumentException("Mã giải " + code + " đã tồn tại.");
        }
        ExternalCompetition saved = competitionRepository.save(ExternalCompetition.builder()
                .competitionCode(code)
                .competitionName(request.getName().trim())
                .organizer(request.getOrganizer().trim())
                .location(request.getLocation().trim())
                .startDate(request.getRaceDate())
                .registrationDeadline(request.getRegistrationDeadline())
                .raceDistanceM(request.getDistanceM())
                .standardEntryFee(request.getEntryFee() != null ? request.getEntryFee() : BigDecimal.ZERO)
                .entryConditions(request.getEntryConditions())
                .isFinished(false)
                .build());
        auditTrail.record("CREATE_TOURNAMENT", "External_Competitions", null, "giải #" + saved.getCompetitionId() + " | " + saved.getCompetitionName());
        return toDto(saved);
    }

    // ---------- Đề xuất thi đấu ----------

    @Transactional(readOnly = true)
    public List<RaceProposalDTO> listProposals(String status) {
        User user = currentUser();
        List<CompetitionEntry> list = switch (user.getRoleName()) {
            case "Trainer" -> entryRepository.findByHorse_AssignedTrainer_UserIdOrderByProposedAtDesc(user.getUserId());
            case "Horse Owner" -> entryRepository.findByHorse_Owner_UserIdOrderByProposedAtDesc(user.getUserId());
            default -> entryRepository.findAllByOrderByProposedAtDesc();
        };
        return list.stream()
                .filter(e -> !StringUtils.hasText(status) || status.trim().equalsIgnoreCase(e.getStatus()))
                .map(RaceProposalService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RaceProposalDTO getProposal(Integer id) {
        CompetitionEntry entry = findEntry(id);
        checkCanView(entry, currentUser());
        return toDto(entry);
    }

    @Transactional
    public RaceProposalDTO create(RaceProposalCreateDTO request) {
        User trainer = currentUser();
        Horse horse = horseRepository.findById(request.getHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + request.getHorseId()));
        horseAccessGuard.checkAssigned(horse);
        if (horse.getAssignedTrainer() == null || !horse.getAssignedTrainer().getUserId().equals(trainer.getUserId())) {
            throw new AccessDeniedException("Chỉ HLV phụ trách ngựa " + horse.getHorseName() + " mới được đề xuất thi đấu");
        }
        if (!"APPROVED".equalsIgnoreCase(horse.getLatestRegistrationStatus()) && !(horse.getLatestRegistrationStatus() == null && horse.getStall() != null)) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " chưa được duyệt vào CLB.");
        }
        if (Boolean.TRUE.equals(horse.getIsLocked())) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " đang bị khóa huấn luyện (VetLock), không thể đề xuất thi đấu.");
        }
        ExternalCompetition competition = competitionRepository.findById(request.getTournamentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giải đấu với ID: " + request.getTournamentId()));
        if (!Boolean.TRUE.equals(toDto(competition).getOpenForProposal())) {
            throw new IllegalArgumentException("Giải " + competition.getCompetitionName() + " đã kết thúc hoặc đã qua hạn đăng ký.");
        }
        if (entryRepository.existsByHorse_HorseIdAndCompetition_CompetitionId(horse.getHorseId(), competition.getCompetitionId())) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " đã có đề xuất cho giải " + competition.getCompetitionName() + ".");
        }

        CompetitionEntry entry = entryRepository.saveAndFlush(CompetitionEntry.builder()
                .competition(competition)
                .horse(horse)
                .status(CompetitionEntry.WAITING_OWNER)
                .proposedBy(trainer)
                .proposedAt(LocalDateTime.now())
                .proposalNote(trimOrNull(request.getNote()))
                .entryFeeCharged(competition.getStandardEntryFee() != null ? competition.getStandardEntryFee() : BigDecimal.ZERO)
                .transportFee(BigDecimal.ZERO)
                .prizeAwarded(BigDecimal.ZERO)
                .build());
        audit("CREATE_RACE_PROPOSAL", null, entry);
        notify(horse.getOwner(), "[HorseTrack] Đề xuất cho ngựa " + horse.getHorseName() + " thi đấu",
                "<p>HLV <b>" + esc(trainer.getFullName()) + "</b> đề xuất ngựa <b>" + esc(horse.getHorseName()) + "</b> tham gia giải <b>"
                        + esc(competition.getCompetitionName()) + "</b> ngày " + competition.getStartDate() + " tại " + esc(competition.getLocation()) + ".</p>"
                        + (entry.getProposalNote() != null ? "<p>Ghi chú: " + esc(entry.getProposalNote()) + "</p>" : "")
                        + "<p>Vui lòng vào HorseTrack để đồng ý hoặc từ chối.</p>");
        return toDto(entry);
    }

    @Transactional
    public RaceProposalDTO ownerResponse(Integer id, OwnerResponseDTO request) {
        User owner = currentUser();
        CompetitionEntry entry = findEntry(id);
        if (entry.getHorse().getOwner() == null || !entry.getHorse().getOwner().getUserId().equals(owner.getUserId())) {
            throw new AccessDeniedException("Bạn không phải chủ của ngựa " + entry.getHorse().getHorseName());
        }
        requireStatus(entry, CompetitionEntry.WAITING_OWNER, "trả lời đề xuất");
        String before = entry.getStatus();
        boolean agree = Boolean.TRUE.equals(request.getAgree());
        entry.setOwnerAgreed(agree);
        entry.setOwnerRespondedAt(LocalDateTime.now());
        entry.setOwnerResponseNote(trimOrNull(request.getNote()));
        entry.setStatus(agree ? CompetitionEntry.WAITING_VET : CompetitionEntry.OWNER_DECLINED);
        entryRepository.save(entry);
        audit(agree ? "OWNER_AGREE_RACE" : "OWNER_DECLINE_RACE", before, entry);
        notify(entry.getProposedBy(), "[HorseTrack] Chủ ngựa " + (agree ? "đồng ý" : "từ chối") + " đề xuất thi đấu " + entry.getEntryCode(),
                "<p>Chủ ngựa <b>" + esc(owner.getFullName()) + "</b> đã <b>" + (agree ? "đồng ý" : "từ chối") + "</b> cho ngựa <b>"
                        + esc(entry.getHorse().getHorseName()) + "</b> tham gia giải <b>" + esc(entry.getCompetition().getCompetitionName()) + "</b>.</p>"
                        + (entry.getOwnerResponseNote() != null ? "<p>Ghi chú: " + esc(entry.getOwnerResponseNote()) + "</p>" : "")
                        + (agree ? "<p>Đề xuất chuyển sang chờ bác sĩ khám tiền thi đấu.</p>" : ""));
        return toDto(entry);
    }

    @Transactional
    public RaceProposalDTO markSubmitted(Integer id) {
        CompetitionEntry entry = findEntryForTrainer(id);
        requireStatus(entry, CompetitionEntry.READY_TO_SUBMIT, "gửi BTC");
        String before = entry.getStatus();
        entry.setStatus(CompetitionEntry.SUBMITTED);
        entryRepository.save(entry);
        audit("SUBMIT_RACE_ENTRY", before, entry);
        return toDto(entry);
    }

    @Transactional
    public RaceProposalDTO organizerRejected(Integer id, OrganizerRejectDTO request) {
        CompetitionEntry entry = findEntryForTrainer(id);
        if (!CompetitionEntry.SUBMITTED.equals(entry.getStatus()) && !CompetitionEntry.ACCEPTED.equals(entry.getStatus())) {
            throw new IllegalArgumentException("Chỉ ghi nhận BTC từ chối khi đề xuất đã gửi BTC (hiện tại: " + label(entry.getStatus()) + ").");
        }
        String before = entry.getStatus();
        entry.setStatus(CompetitionEntry.REJECTED);
        entry.setResultNote(request.getReason().trim());
        entryRepository.save(entry);
        audit("ORGANIZER_REJECT_RACE", before, entry);
        notify(entry.getHorse().getOwner(), "[HorseTrack] BTC từ chối đăng ký " + entry.getEntryCode(),
                "<p>BTC giải <b>" + esc(entry.getCompetition().getCompetitionName()) + "</b> đã từ chối đăng ký của ngựa <b>"
                        + esc(entry.getHorse().getHorseName()) + "</b>.</p><p>Lý do: " + esc(entry.getResultNote()) + "</p>");
        return toDto(entry);
    }

    @Transactional
    public RaceProposalDTO recordResult(Integer id, RaceResultDTO request) {
        CompetitionEntry entry = findEntryForTrainer(id);
        if (!CompetitionEntry.SUBMITTED.equals(entry.getStatus()) && !CompetitionEntry.ACCEPTED.equals(entry.getStatus())) {
            throw new IllegalArgumentException("Chỉ ghi kết quả cho đề xuất đã gửi BTC (hiện tại: " + label(entry.getStatus()) + ").");
        }
        if (entry.getCompetition().getStartDate() != null && entry.getCompetition().getStartDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Giải diễn ra ngày " + entry.getCompetition().getStartDate() + ", chưa thể ghi kết quả.");
        }
        String before = entry.getStatus();
        entry.setRankPosition(request.getRank());
        entry.setFinishTimeSeconds(parseFinishTime(request.getFinishTime()));
        entry.setResultNote(trimOrNull(request.getNote()));
        entry.setStatus(CompetitionEntry.RESULT_RECORDED);
        entryRepository.save(entry);
        audit("RECORD_RACE_RESULT", before, entry);
        notify(entry.getHorse().getOwner(), "[HorseTrack] Kết quả thi đấu của ngựa " + entry.getHorse().getHorseName(),
                "<p>Ngựa <b>" + esc(entry.getHorse().getHorseName()) + "</b> về hạng <b>" + entry.getRankPosition() + "</b> tại giải <b>"
                        + esc(entry.getCompetition().getCompetitionName()) + "</b>"
                        + (entry.getFinishTimeSeconds() != null ? " với thời gian " + entry.getFinishTimeSeconds().stripTrailingZeros().toPlainString() + " giây" : "") + ".</p>");
        return toDto(entry);
    }

    // ---------- Hỗ trợ ----------

    public static TournamentDTO toDto(ExternalCompetition c) {
        LocalDate today = LocalDate.now();
        boolean open = !Boolean.TRUE.equals(c.getIsFinished())
                && (c.getStartDate() == null || !c.getStartDate().isBefore(today))
                && (c.getRegistrationDeadline() == null || !c.getRegistrationDeadline().isBefore(today));
        return TournamentDTO.builder()
                .tournamentId(c.getCompetitionId())
                .code(c.getCompetitionCode())
                .name(c.getCompetitionName())
                .organizer(c.getOrganizer())
                .location(c.getLocation())
                .raceDate(c.getStartDate())
                .registrationDeadline(c.getRegistrationDeadline())
                .distanceM(c.getRaceDistanceM())
                .entryFee(c.getStandardEntryFee())
                .entryConditions(c.getEntryConditions())
                .finished(c.getIsFinished())
                .openForProposal(open)
                .build();
    }

    public static RaceProposalDTO toDto(CompetitionEntry e) {
        Horse h = e.getHorse();
        return RaceProposalDTO.builder()
                .proposalId(e.getEntryId())
                .proposalCode(e.getEntryCode())
                .status(e.getStatus())
                .statusLabel(label(e.getStatus()))
                .horseId(h != null ? h.getHorseId() : null)
                .horseName(h != null ? h.getHorseName() : null)
                .ownerId(h != null && h.getOwner() != null ? h.getOwner().getUserId() : null)
                .ownerName(h != null && h.getOwner() != null ? h.getOwner().getFullName() : null)
                .tournament(e.getCompetition() != null ? toDto(e.getCompetition()) : null)
                .proposedById(e.getProposedBy() != null ? e.getProposedBy().getUserId() : null)
                .proposedByName(e.getProposedBy() != null ? e.getProposedBy().getFullName() : null)
                .proposedAt(e.getProposedAt())
                .note(e.getProposalNote())
                .ownerAgreed(e.getOwnerAgreed())
                .ownerRespondedAt(e.getOwnerRespondedAt())
                .ownerNote(e.getOwnerResponseNote())
                .vetName(e.getVet() != null ? e.getVet().getFullName() : null)
                .vetPassed(e.getVetPassed())
                .vetCheckedAt(e.getVetCheckedAt())
                .vetNote(e.getVetNote())
                .entryFee(e.getEntryFeeCharged())
                .rank(e.getRankPosition())
                .finishTimeSeconds(e.getFinishTimeSeconds())
                .resultNote(e.getResultNote())
                .build();
    }

    public static String label(String status) {
        return STATUS_LABELS.getOrDefault(status, status);
    }

    // "72.45" giây hoặc "1:12.45" (phút:giây)
    static BigDecimal parseFinishTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String v = value.trim();
        try {
            BigDecimal seconds;
            if (v.contains(":")) {
                String[] parts = v.split(":");
                if (parts.length != 2) throw new NumberFormatException();
                seconds = new BigDecimal(parts[0]).multiply(BigDecimal.valueOf(60)).add(new BigDecimal(parts[1]));
            } else {
                seconds = new BigDecimal(v);
            }
            if (seconds.signum() <= 0) throw new NumberFormatException();
            return seconds.setScale(3, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Thời gian về đích '" + value + "' không hợp lệ (ví dụ 72.45 hoặc 1:12.45).");
        }
    }

    private void checkCanView(CompetitionEntry e, User user) {
        Horse h = e.getHorse();
        switch (user.getRoleName()) {
            case "Trainer" -> {
                if (h.getAssignedTrainer() == null || !h.getAssignedTrainer().getUserId().equals(user.getUserId()))
                    throw new AccessDeniedException("Bạn không phụ trách ngựa " + h.getHorseName());
            }
            case "Horse Owner" -> {
                if (h.getOwner() == null || !h.getOwner().getUserId().equals(user.getUserId()))
                    throw new AccessDeniedException("Bạn không phải chủ của ngựa " + h.getHorseName());
            }
            default -> { }
        }
    }

    private CompetitionEntry findEntryForTrainer(Integer id) {
        User trainer = currentUser();
        CompetitionEntry entry = findEntry(id);
        Horse h = entry.getHorse();
        if (h.getAssignedTrainer() == null || !h.getAssignedTrainer().getUserId().equals(trainer.getUserId())) {
            throw new AccessDeniedException("Chỉ HLV phụ trách ngựa " + h.getHorseName() + " mới được cập nhật đề xuất");
        }
        return entry;
    }

    private static void requireStatus(CompetitionEntry e, String status, String action) {
        if (!status.equals(e.getStatus())) {
            throw new IllegalArgumentException("Chỉ " + action + " khi đề xuất đang ở trạng thái '" + label(status) + "' (hiện tại: " + label(e.getStatus()) + ").");
        }
    }

    private void audit(String action, String before, CompetitionEntry e) {
        auditTrail.record(action, "Competition_Entries", before == null ? null : "status=" + before,
                "status=" + e.getStatus() + " | đề xuất " + (e.getEntryCode() != null ? e.getEntryCode() : "#" + e.getEntryId())
                        + " | ngựa " + e.getHorse().getHorseName() + " | giải " + e.getCompetition().getCompetitionName());
    }

    private void notify(User to, String subject, String html) {
        if (to == null || !StringUtils.hasText(to.getEmail())) {
            return;
        }
        try {
            emailService.sendAsync("RACE_PROPOSAL", to.getEmail(), subject, "<p>Xin chào <b>" + esc(to.getFullName()) + "</b>,</p>" + html);
        } catch (Exception ex) {
            log.warn("Không gửi được email đề xuất thi đấu: {}", ex.getMessage());
        }
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

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(String.valueOf(s));
    }
}
