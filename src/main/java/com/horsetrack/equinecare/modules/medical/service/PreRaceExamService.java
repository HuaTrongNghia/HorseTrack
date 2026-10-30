package com.horsetrack.equinecare.modules.medical.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.medical.dto.PreRaceExamDTO;
import com.horsetrack.equinecare.modules.medical.dto.SubmitExamResultDTO;
import com.horsetrack.equinecare.modules.tournament.entity.CompetitionEntry;
import com.horsetrack.equinecare.modules.tournament.entity.ExternalCompetition;
import com.horsetrack.equinecare.modules.tournament.repository.CompetitionEntryRepository;
import com.horsetrack.equinecare.modules.tournament.service.RaceProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Khám tiền thi đấu trên đề xuất thật (Competition_Entries): tab CHO_KHAM = đề xuất HLV đã xác nhận (WAITING_VET_EXAM, có hạn khám);
 * tab DA_CO_KET_QUA = đề xuất bác sĩ đã kết luận. Đạt -> tự lập hóa đơn lệ phí, WAITING_PAYMENT (hạn 2 ngày);
 * không đạt -> VET_FAILED, mất suất và đôn hàng chờ (RaceProposalService.afterVetExam).
 */
@Service
@RequiredArgsConstructor
public class PreRaceExamService {

    public static final String TAB_WAITING = "CHO_KHAM";
    public static final String TAB_DONE = "DA_CO_KET_QUA";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> HEALTH = Map.of(
            "HEALTHY", "Khỏe mạnh", "WATCH", "Theo dõi", "INJURED", "Chấn thương", "QUARANTINE", "Cách ly", "EMERGENCY", "Cấp cứu");

    private final CompetitionEntryRepository entryRepository;
    private final UserRepository userRepository;
    private final RaceProposalService raceProposalService;

    // Lấy danh sách theo Tab
    @Transactional(readOnly = true)
    public List<PreRaceExamDTO> getExamsByTab(String tabStatus) {
        List<CompetitionEntry> list = TAB_DONE.equalsIgnoreCase(tabStatus)
                ? entryRepository.findByVetPassedIsNotNullOrderByVetCheckedAtDesc()
                : entryRepository.findByStatusInOrderByProposedAtAsc(List.of(CompetitionEntry.WAITING_VET_EXAM));
        return list.stream().map(PreRaceExamService::toDto).toList();
    }

    // Bác sĩ ghi kết luận khám; proposalCode là mã DX-0001 hoặc ID đề xuất
    @Transactional
    public void submitExamResult(String proposalCode, SubmitExamResultDTO dto) {
        if (dto.getIsPassed() == null) {
            throw new IllegalArgumentException("Phải chọn kết luận Đạt / Không đạt (isPassed).");
        }
        CompetitionEntry entry = (proposalCode.matches("\\d+") ? entryRepository.findById(Integer.valueOf(proposalCode)) : entryRepository.findByEntryCode(proposalCode))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề xuất thi đấu " + proposalCode));
        if (!CompetitionEntry.WAITING_VET_EXAM.equals(entry.getStatus())) {
            throw new IllegalArgumentException("Đề xuất " + proposalCode + " không ở trạng thái chờ bác sĩ khám (hiện tại: "
                    + RaceProposalService.label(entry.getStatus()) + ").");
        }
        boolean passed = dto.getIsPassed();
        Horse horse = entry.getHorse();
        if (passed && Boolean.TRUE.equals(horse.getIsLocked())) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " đang bị khóa huấn luyện (VetLock), không thể kết luận đạt.");
        }
        if (!passed && !StringUtils.hasText(dto.getVetNotes())) {
            throw new IllegalArgumentException("Phải ghi lý do khi kết luận không đạt.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (entry.getStageDeadline() != null && !now.isBefore(entry.getStageDeadline())) {
            throw new IllegalArgumentException("Đã quá hạn khám (" + entry.getStageDeadline().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))
                    + "), đề xuất sẽ bị hệ thống hủy.");
        }
        User vet = currentUser();
        entry.setVet(vet);
        entry.setVetPassed(passed);
        entry.setVetCheckedAt(now);
        entry.setVetNote(StringUtils.hasText(dto.getVetNotes()) ? truncate(dto.getVetNotes().trim()) : null);
        // Đạt: lập hóa đơn lệ phí và chờ thanh toán; không đạt: mất suất, đôn hàng chờ (kèm nhật ký và thông báo)
        raceProposalService.afterVetExam(entry);
    }

    private static PreRaceExamDTO toDto(CompetitionEntry e) {
        Horse h = e.getHorse();
        ExternalCompetition c = e.getCompetition();
        String warning = Boolean.TRUE.equals(h.getIsLocked()) ? "Đang khóa huấn luyện (VetLock)"
                : !"HEALTHY".equalsIgnoreCase(h.getHealthStatus()) ? "Sức khỏe: " + HEALTH.getOrDefault(h.getHealthStatus(), h.getHealthStatus()) : null;
        boolean done = e.getVetPassed() != null;
        return PreRaceExamDTO.builder()
                .proposalCode(e.getEntryCode())
                .proposalDate(e.getProposedAt() != null ? e.getProposedAt().format(DATE) : null)
                .horseName(h.getHorseName())
                .ownerName(h.getOwner() != null ? h.getOwner().getFullName() : null)
                .tournamentName(c.getCompetitionName())
                .raceDate(c.getStartDate() != null ? c.getStartDate().format(DATE) : null)
                .distance(c.getRaceDistanceM() != null ? c.getRaceDistanceM().stripTrailingZeros().toPlainString() + " m" : null)
                .location(c.getLocation())
                .trainerName(e.getTrainer() != null ? e.getTrainer().getFullName() : h.getAssignedTrainer() != null ? h.getAssignedTrainer().getFullName() : null)
                .examDeadline(!done && e.getStageDeadline() != null ? e.getStageDeadline().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")) : null)
                .healthStatus(HEALTH.getOrDefault(h.getHealthStatus(), h.getHealthStatus()))
                .warningTag(warning)
                .tabStatus(done ? TAB_DONE : TAB_WAITING)
                .resultStatus(done ? (e.getVetPassed() ? "ĐẠT" : "KHÔNG ĐẠT") : null)
                .vetNotes(e.getVetNote())
                .nextStepStatus(done ? RaceProposalService.label(e.getStatus()) : null)
                .build();
    }

    private static String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }
}
