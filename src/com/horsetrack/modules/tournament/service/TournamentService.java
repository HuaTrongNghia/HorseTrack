package com.horsetrack.equinecare.modules.tournament.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.horsetrack.equinecare.modules.tournament.service.RaceNotifier.esc;

/**
 * Giải đấu: chỉ Quản lý CLB tạo, sửa, công bố và nhập kết quả. Giải đã có người đề xuất thì không sửa được điều kiện,
 * ngày thi, hạn đăng ký, lệ phí (chỉ được tăng số suất). Kết quả nhập sau ngày thi cho đề xuất REGISTERED,
 * tự tính tiền thưởng theo bảng thưởng.
 */
@Service
@RequiredArgsConstructor
public class TournamentService {

    /** Ngày thi phải cách hôm nay ít nhất 5 ngày; hạn đăng ký trước ngày thi ít nhất 3 ngày */
    static final int MIN_LEAD_DAYS = 5;
    static final int DEADLINE_GAP_DAYS = 3;

    private final ExternalCompetitionRepository competitionRepository;
    private final CompetitionEntryRepository entryRepository;
    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final HorseEligibilityService eligibility;
    private final RaceSlotAllocator slots;
    private final RaceMapper mapper;
    private final RaceNotifier notifier;
    private final AuditTrail auditTrail;

    // ---------- Danh sách / chi tiết ----------

    /** Quản lý CLB thấy cả giải nháp; vai trò khác chỉ thấy giải đã công bố / đã kết thúc. open = true: chỉ giải còn nhận đề xuất */
    @Transactional(readOnly = true)
    public List<TournamentDTO> list(boolean openOnly) {
        boolean manager = isManager();
        List<ExternalCompetition> list = manager ? competitionRepository.findAllByOrderByStartDateDesc()
                : competitionRepository.findByStatusInOrderByStartDateDesc(List.of(ExternalCompetition.PUBLISHED, ExternalCompetition.FINISHED));
        return list.stream().map(mapper::toDto).filter(t -> !openOnly || Boolean.TRUE.equals(t.getOpenForProposal())).toList();
    }

    @Transactional(readOnly = true)
    public TournamentDTO get(Integer id) {
        return mapper.toDto(findVisible(id));
    }

    // ---------- Quản lý CLB: tạo / sửa / công bố ----------

    @Transactional
    public TournamentDTO create(TournamentCreateDTO request) {
        validate(request, null);
        validateDates(request);
        ExternalCompetition c = ExternalCompetition.builder()
                .competitionCode(trimOrNull(request.getCode()))
                .isFinished(false)
                .status(ExternalCompetition.DRAFT)
                .build();
        apply(c, request, true);
        ExternalCompetition saved = competitionRepository.save(c);
        auditTrail.record("CREATE_TOURNAMENT", "External_Competitions", null, "giải #" + saved.getCompetitionId() + " | " + saved.getCompetitionName() + " | DRAFT");
        return mapper.toDto(saved);
    }

    @Transactional
    public TournamentDTO update(Integer id, TournamentCreateDTO request) {
        ExternalCompetition c = competitionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giải đấu với ID: " + id));
        if (ExternalCompetition.FINISHED.equals(c.getStatus()) || Boolean.TRUE.equals(c.getIsFinished())) {
            throw new IllegalArgumentException("Giải " + c.getCompetitionName() + " đã kết thúc, không sửa được.");
        }
        validate(request, id);
        // Giải cũ chỉ sửa tên / địa điểm... thì không bắt lại luật ngày (có thể đã sát ngày thi)
        if (!Objects.equals(c.getStartDate(), request.getRaceDate()) || !Objects.equals(c.getRegistrationDeadline(), request.getRegistrationDeadline())) {
            validateDates(request);
        }
        boolean locked = entryRepository.existsByCompetition_CompetitionId(id);
        if (locked) {
            List<String> changed = lockedChanges(c, request);
            if (!changed.isEmpty()) {
                throw new IllegalArgumentException("Giải đã có người đề xuất nên không sửa được: " + String.join(", ", changed)
                        + ". Chỉ được sửa tên, ban tổ chức, địa điểm, cự ly, mô tả điều kiện, bảng thưởng và tăng số suất.");
            }
            if (request.getSlots() < c.getSlots()) {
                throw new IllegalArgumentException("Giải đã có người đề xuất nên chỉ được tăng số suất (hiện tại " + c.getSlots() + ").");
            }
        }
        String before = "status=" + c.getStatus() + " | slots=" + c.getSlots();
        c.setCompetitionCode(trimOrNull(request.getCode()));
        apply(c, request, !locked);
        competitionRepository.save(c);
        auditTrail.record("UPDATE_TOURNAMENT", "External_Competitions", before,
                "giải #" + c.getCompetitionId() + " | " + c.getCompetitionName() + " | slots=" + c.getSlots());
        // Tăng suất: đôn hàng chờ lên
        slots.promoteWaitlist(c, LocalDateTime.now());
        return mapper.toDto(c);
    }

    @Transactional
    public TournamentDTO publish(Integer id) {
        ExternalCompetition c = competitionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giải đấu với ID: " + id));
        if (!ExternalCompetition.DRAFT.equals(c.getStatus())) {
            throw new IllegalArgumentException("Chỉ công bố giải đang ở trạng thái Nháp (hiện tại: " + RaceMapper.TOURNAMENT_LABELS.getOrDefault(c.getStatus(), c.getStatus()) + ").");
        }
        if (!LocalDateTime.now().isBefore(c.registrationCloseAt())) {
            throw new IllegalArgumentException("Hạn đăng ký " + c.getRegistrationDeadline() + " đã qua, hãy sửa hạn đăng ký trước khi công bố.");
        }
        c.setStatus(ExternalCompetition.PUBLISHED);
        c.setPublishedAt(LocalDateTime.now());
        competitionRepository.save(c);
        auditTrail.record("PUBLISH_TOURNAMENT", "External_Competitions", "status=DRAFT", "status=PUBLISHED | giải #" + c.getCompetitionId() + " | " + c.getCompetitionName());
        notifier.notifyRole("Horse Owner", "[HorseTrack] Giải đấu mới: " + c.getCompetitionName(),
                "<p>CLB vừa công bố giải <b>" + esc(c.getCompetitionName()) + "</b> ngày " + c.getStartDate() + " tại " + esc(c.getLocation())
                        + " (" + c.getSlots() + " suất, hạn đăng ký " + c.getRegistrationDeadline() + ").</p>"
                        + "<p>Vào HorseTrack để xem ngựa nào của bạn đủ điều kiện và gửi đề xuất.</p>", "/owner/tournaments");
        return mapper.toDto(c);
    }

    // ---------- Quản lý CLB: kết thúc giải, nhập kết quả ----------

    /**
     * Kết thúc giải (cho phép trước ngày thi để demo): ngừng nhận đề xuất, đóng các đề xuất chưa đăng ký xong (NO_SLOT, hủy hóa đơn
     * lệ phí chưa trả), chỉ giữ đề xuất REGISTERED để nhập kết quả. Không còn ai REGISTERED thì giải chuyển FINISHED luôn.
     */
    @Transactional
    public TournamentDTO finish(Integer id) {
        ExternalCompetition c = competitionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giải đấu với ID: " + id));
        if (!ExternalCompetition.PUBLISHED.equals(c.getStatus())) {
            throw new IllegalArgumentException("Chỉ kết thúc được giải đang công bố (hiện tại: " + RaceMapper.tournamentLabel(c) + ").");
        }
        if (Boolean.TRUE.equals(c.getIsFinished())) {
            throw new IllegalArgumentException("Giải " + c.getCompetitionName() + " đã kết thúc, đang chờ nhập kết quả.");
        }
        List<CompetitionEntry> open = entryRepository.findByCompetition_CompetitionIdAndStatusInOrderByProposedAtAscEntryIdAsc(id, List.of(
                CompetitionEntry.WAITLISTED, CompetitionEntry.WAITING_TRAINER, CompetitionEntry.WAITING_VET_EXAM, CompetitionEntry.WAITING_PAYMENT));
        // Đang có giao dịch VNPay chờ xác minh: đợi kết quả thanh toán rồi mới kết thúc
        for (CompetitionEntry e : open) {
            if (e.getInvoice() != null && RaceSlotAllocator.hasLiveTransaction(e.getInvoice())) {
                throw new IllegalArgumentException("Đề xuất " + RaceNotifier.code(e) + " đang có giao dịch thanh toán lệ phí chờ xác minh. Vui lòng đợi kết quả thanh toán rồi kết thúc giải.");
            }
        }
        // Đánh dấu trước để việc đóng đề xuất không đôn hàng chờ lên
        c.setIsFinished(true);
        competitionRepository.saveAndFlush(c);
        LocalDateTime now = LocalDateTime.now();
        for (CompetitionEntry e : open) {
            slots.close(e, CompetitionEntry.NO_SLOT, "Quản lý CLB đã kết thúc giải khi đề xuất chưa hoàn tất đăng ký", "RACE_TOURNAMENT_FINISHED", now);
        }
        long registered = entryRepository.countByCompetition_CompetitionIdAndStatusIn(id, List.of(CompetitionEntry.REGISTERED));
        if (registered == 0) {
            c.setStatus(ExternalCompetition.FINISHED);
        }
        competitionRepository.save(c);
        auditTrail.record("FINISH_TOURNAMENT", "External_Competitions", "status=PUBLISHED",
                "status=" + c.getStatus() + " | is_finished=1 | giải #" + id + " | " + c.getCompetitionName() + " | đóng " + open.size()
                        + " đề xuất chưa hoàn tất | " + registered + " đề xuất chờ nhập kết quả");
        return mapper.toDto(c);
    }

    @Transactional
    public List<RaceProposalDTO> recordResults(Integer id, List<RaceResultItemDTO> items) {
        ExternalCompetition c = competitionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giải đấu với ID: " + id));
        if (ExternalCompetition.DRAFT.equals(c.getStatus())) {
            throw new IllegalArgumentException("Giải chưa công bố, chưa thể nhập kết quả.");
        }
        if (!c.raceEnded()) {
            throw new IllegalArgumentException("Giải diễn ra ngày " + c.getStartDate() + ". Bấm \"Kết thúc giải\" trước, hoặc đợi đến ngày thi rồi nhập kết quả.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Danh sách kết quả trống.");
        }
        Set<Integer> ids = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        for (RaceResultItemDTO it : items) {
            if (it.getProposalId() == null || it.getRank() == null || it.getRank() < 1 || it.getRank() > 100) {
                throw new IllegalArgumentException("Mỗi dòng kết quả cần proposalId và thứ hạng từ 1 đến 100.");
            }
            if (!ids.add(it.getProposalId())) {
                throw new IllegalArgumentException("Đề xuất #" + it.getProposalId() + " xuất hiện 2 lần trong danh sách kết quả.");
            }
            if (!ranks.add(it.getRank())) {
                throw new IllegalArgumentException("Hạng " + it.getRank() + " bị trùng trong danh sách kết quả.");
            }
        }
        // Hạng đã ghi cho đề xuất khác (không có trong lần nhập này) cũng không được trùng
        for (CompetitionEntry other : entryRepository.findByCompetition_CompetitionIdAndStatusInOrderByProposedAtAscEntryIdAsc(id, List.of(CompetitionEntry.RESULT_RECORDED))) {
            if (!ids.contains(other.getEntryId()) && other.getRankPosition() != null && ranks.contains(other.getRankPosition())) {
                throw new IllegalArgumentException("Hạng " + other.getRankPosition() + " đã ghi cho đề xuất " + RaceNotifier.code(other) + ".");
            }
        }
        List<RaceProposalDTO> out = new ArrayList<>();
        for (RaceResultItemDTO it : items) {
            CompetitionEntry e = entryRepository.findById(it.getProposalId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề xuất thi đấu với ID: " + it.getProposalId()));
            if (!e.getCompetition().getCompetitionId().equals(id)) {
                throw new IllegalArgumentException("Đề xuất " + RaceNotifier.code(e) + " không thuộc giải " + c.getCompetitionName() + ".");
            }
            if (!CompetitionEntry.REGISTERED.equals(e.getStatus()) && !CompetitionEntry.RESULT_RECORDED.equals(e.getStatus())) {
                throw new IllegalArgumentException("Chỉ nhập kết quả cho đề xuất đã đăng ký thi đấu (REGISTERED); đề xuất " + RaceNotifier.code(e)
                        + " đang " + RaceNotifier.label(e.getStatus()) + ".");
            }
            String before = e.getStatus();
            BigDecimal prize = c.getPrizes().getOrDefault(it.getRank(), BigDecimal.ZERO);
            e.setRankPosition(it.getRank());
            e.setFinishTimeSeconds(parseFinishTime(it.getFinishTime()));
            e.setResultNote(trimOrNull(it.getNote()));
            e.setPrizeAwarded(prize);
            e.setStatus(CompetitionEntry.RESULT_RECORDED);
            entryRepository.save(e);
            notifier.audit("RECORD_RACE_RESULT", before, e, "hạng " + it.getRank() + " | thưởng " + prize.stripTrailingZeros().toPlainString());
            notifier.notify(e.getOwner(), "[HorseTrack] Kết quả thi đấu của ngựa " + e.getHorse().getHorseName(),
                    "<p>Ngựa <b>" + esc(e.getHorse().getHorseName()) + "</b> về hạng <b>" + e.getRankPosition() + "</b> tại giải <b>"
                            + esc(c.getCompetitionName()) + "</b>"
                            + (e.getFinishTimeSeconds() != null ? " với thời gian " + e.getFinishTimeSeconds().stripTrailingZeros().toPlainString() + " giây" : "") + ".</p>"
                            + (prize.signum() > 0 ? "<p>Tiền thưởng: <b>" + money(prize) + "</b>.</p>" : "")
                            + "<p>Thành tích đã được cập nhật vào hồ sơ ngựa.</p>");
            out.add(mapper.toDto(e));
        }
        // Mọi đề xuất đã đăng ký đều có kết quả: giải kết thúc
        if (entryRepository.countByCompetition_CompetitionIdAndStatusIn(id, List.of(CompetitionEntry.REGISTERED)) == 0) {
            c.setStatus(ExternalCompetition.FINISHED);
            c.setIsFinished(true);
            competitionRepository.save(c);
            auditTrail.record("FINISH_TOURNAMENT", "External_Competitions", "status=PUBLISHED", "status=FINISHED | giải #" + id + " | " + c.getCompetitionName());
        }
        return out;
    }

    // ---------- Chủ ngựa: giải đấu kèm lọc điều kiện ----------

    @Transactional(readOnly = true)
    public List<OwnerTournamentDTO> ownerTournaments(boolean openOnly) {
        User owner = currentUser();
        List<Horse> horses = approvedHorses(owner);
        return competitionRepository.findByStatusInOrderByStartDateDesc(List.of(ExternalCompetition.PUBLISHED, ExternalCompetition.FINISHED)).stream()
                .filter(c -> !openOnly || RaceMapper.isOpen(c))
                .map(c -> ownerView(c, owner, horses))
                .toList();
    }

    @Transactional(readOnly = true)
    public OwnerTournamentDTO ownerTournament(Integer id) {
        User owner = currentUser();
        ExternalCompetition c = findVisible(id);
        return ownerView(c, owner, approvedHorses(owner));
    }

    // Chỉ ngựa đã duyệt hồ sơ và được cấp chuồng mới hiện trong danh sách chọn ngựa đi giải
    private List<Horse> approvedHorses(User owner) {
        return horseRepository.findByOwnerUserId(owner.getUserId()).stream()
                .filter(h -> Horse.APPROVAL_APPROVED.equals(h.getApprovalStatus()) && h.getStall() != null)
                .toList();
    }

    private OwnerTournamentDTO ownerView(ExternalCompetition c, User owner, List<Horse> horses) {
        List<HorseEligibilityDTO> ranked = eligibility.rank(horses, c);
        List<CompetitionEntry> mine = entryRepository.findByCompetition_CompetitionIdAndOwner_UserIdOrderByProposedAtDesc(c.getCompetitionId(), owner.getUserId());
        CompetitionEntry active = mine.stream().filter(e -> CompetitionEntry.ACTIVE.contains(e.getStatus())).findFirst().orElse(null);
        CompetitionEntry last = active == null ? mine.stream().findFirst().orElse(null) : null;
        boolean complaintPending = mine.stream().anyMatch(e -> CompetitionEntry.COMPLAINT_PENDING.equals(e.getComplaintStatus()));
        boolean anyEligible = ranked.stream().anyMatch(HorseEligibilityDTO::getEligible);
        String reason = !RaceMapper.isOpen(c) ? "Giải đã hết hạn đăng ký hoặc đã kết thúc"
                : active != null ? "Bạn đã có đề xuất " + RaceNotifier.code(active) + " còn hiệu lực cho giải này"
                : complaintPending ? "Bạn đang có khiếu nại chờ Quản lý CLB xử lý cho giải này"
                : horses.isEmpty() ? "Bạn chưa có ngựa nào được duyệt hồ sơ"
                : !anyEligible ? "Không có ngựa nào đủ điều kiện" : null;
        TournamentDTO t = mapper.toDto(c);
        return OwnerTournamentDTO.builder()
                .tournament(t)
                .eligible(anyEligible)
                .horses(ranked)
                .recommendedHorseId(ranked.stream().filter(HorseEligibilityDTO::getRecommended).map(HorseEligibilityDTO::getHorseId).findFirst().orElse(null))
                .myProposal(active != null ? mapper.toDto(active) : null)
                .lastProposal(last != null ? mapper.toDto(last) : null)
                .slotsLeft(t.getSlotsLeft())
                .queueLength(t.getQueueLength())
                .canPropose(reason == null)
                .cannotProposeReason(reason)
                .build();
    }

    // ---------- Hỗ trợ ----------

    private ExternalCompetition findVisible(Integer id) {
        ExternalCompetition c = competitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giải đấu với ID: " + id));
        if (ExternalCompetition.DRAFT.equals(c.getStatus()) && !isManager()) {
            throw new ResourceNotFoundException("Không tìm thấy giải đấu với ID: " + id);
        }
        return c;
    }

    /** Ngày thi >= hôm nay + 5 ngày; hạn đăng ký <= ngày thi - 3 ngày (đủ thời gian HLV xác nhận, khám, thanh toán) */
    private static void validateDates(TournamentCreateDTO r) {
        LocalDate earliest = LocalDate.now().plusDays(MIN_LEAD_DAYS);
        if (r.getRaceDate().isBefore(earliest)) {
            throw new IllegalArgumentException("Ngày thi đấu phải từ " + earliest + " trở đi (ít nhất " + MIN_LEAD_DAYS + " ngày kể từ hôm nay).");
        }
        LocalDate latestDeadline = r.getRaceDate().minusDays(DEADLINE_GAP_DAYS);
        if (r.getRegistrationDeadline().isAfter(latestDeadline)) {
            throw new IllegalArgumentException("Hạn đăng ký phải trước ngày thi ít nhất " + DEADLINE_GAP_DAYS + " ngày (chậm nhất " + latestDeadline + ").");
        }
    }

    private void validate(TournamentCreateDTO r, Integer id) {
        if (r.getRegistrationDeadline().isAfter(r.getRaceDate())) {
            throw new IllegalArgumentException("Hạn đăng ký phải trước hoặc bằng ngày thi đấu.");
        }
        if (r.getAllowedBreeds().stream().noneMatch(StringUtils::hasText)) {
            throw new IllegalArgumentException("Phải chọn ít nhất 1 giống ngựa được tham gia.");
        }
        if (r.getPrizes().stream().noneMatch(p -> p.getAmount() != null && p.getAmount().signum() > 0)) {
            throw new IllegalArgumentException("Bảng thưởng phải có ít nhất 1 hạng có tiền thưởng lớn hơn 0.");
        }
        if (r.getMinAgeYears() != null && r.getMaxAgeYears() != null && r.getMinAgeYears() > r.getMaxAgeYears()) {
            throw new IllegalArgumentException("Tuổi tối thiểu không được lớn hơn tuổi tối đa.");
        }
        if (r.getMinWeightKg() != null && r.getMaxWeightKg() != null && r.getMinWeightKg().compareTo(r.getMaxWeightKg()) > 0) {
            throw new IllegalArgumentException("Cân nặng tối thiểu không được lớn hơn cân nặng tối đa.");
        }
        if (r.getPrizes() != null) {
            Set<Integer> seen = new HashSet<>();
            for (PrizeDTO p : r.getPrizes()) {
                if (!seen.add(p.getRank())) {
                    throw new IllegalArgumentException("Bảng thưởng có hạng " + p.getRank() + " bị trùng.");
                }
            }
        }
        String breeds = joinBreeds(r.getAllowedBreeds());
        if (breeds != null && breeds.length() > 500) {
            throw new IllegalArgumentException("Danh sách giống quá dài (tối đa 500 ký tự).");
        }
        String code = trimOrNull(r.getCode());
        if (code != null && (id == null ? competitionRepository.existsByCompetitionCode(code) : competitionRepository.existsByCompetitionCodeAndCompetitionIdNot(code, id))) {
            throw new IllegalArgumentException("Mã giải " + code + " đã tồn tại.");
        }
    }

    // Các trường bị khóa khi đã có đề xuất mà request muốn đổi
    private static List<String> lockedChanges(ExternalCompetition c, TournamentCreateDTO r) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(c.getStartDate(), r.getRaceDate())) changed.add("ngày thi");
        if (!Objects.equals(c.getRegistrationDeadline(), r.getRegistrationDeadline())) changed.add("hạn đăng ký");
        if (!sameMoney(c.getStandardEntryFee(), r.getEntryFee() == null ? BigDecimal.ZERO : r.getEntryFee())) changed.add("lệ phí");
        if (!Objects.equals(c.getMinAgeYears(), r.getMinAgeYears()) || !Objects.equals(c.getMaxAgeYears(), r.getMaxAgeYears())) changed.add("điều kiện tuổi");
        if (!sameMoney(c.getMinWeightKg(), r.getMinWeightKg()) || !sameMoney(c.getMaxWeightKg(), r.getMaxWeightKg())) changed.add("điều kiện cân nặng");
        if (!breedSet(c.getAllowedBreeds()).equals(breedSet(joinBreeds(r.getAllowedBreeds())))) {
            changed.add("giống được phép");
        }
        return changed;
    }

    // conditions = true: ghi cả các trường bị khóa (giải chưa có đề xuất)
    private static void apply(ExternalCompetition c, TournamentCreateDTO r, boolean conditions) {
        c.setCompetitionName(r.getName().trim());
        c.setOrganizer(r.getOrganizer().trim());
        c.setLocation(r.getLocation().trim());
        c.setRaceDistanceM(r.getDistanceM());
        c.setEntryConditions(trimOrNull(r.getEntryConditions()));
        c.setSlots(r.getSlots());
        c.getPrizes().clear();
        if (r.getPrizes() != null) {
            r.getPrizes().forEach(p -> c.getPrizes().put(p.getRank(), p.getAmount()));
        }
        if (conditions) {
            c.setStartDate(r.getRaceDate());
            c.setRegistrationDeadline(r.getRegistrationDeadline());
            c.setStandardEntryFee(r.getEntryFee() != null ? r.getEntryFee() : BigDecimal.ZERO);
            c.setMinAgeYears(r.getMinAgeYears());
            c.setMaxAgeYears(r.getMaxAgeYears());
            c.setMinWeightKg(r.getMinWeightKg());
            c.setMaxWeightKg(r.getMaxWeightKg());
            c.setAllowedBreeds(joinBreeds(r.getAllowedBreeds()));
        }
    }

    private static String joinBreeds(List<String> breeds) {
        if (breeds == null) {
            return null;
        }
        List<String> clean = breeds.stream().filter(StringUtils::hasText).map(b -> b.trim().replace(";", ",")).distinct().toList();
        return clean.isEmpty() ? null : String.join(";", clean);
    }

    private static Set<String> breedSet(String stored) {
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.addAll(HorseEligibilityService.breeds(stored));
        return set;
    }

    private static boolean sameMoney(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
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

    static String money(BigDecimal v) {
        return String.format("%,.0f VND", v).replace(',', '.');
    }

    private boolean isManager() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "Club Manager".equals(a.getAuthority()));
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
        if (!"Horse Owner".equals(u.getRoleName())) {
            throw new AccessDeniedException("Chỉ chủ ngựa dùng được chức năng này");
        }
        return u;
    }

    private static String trimOrNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
