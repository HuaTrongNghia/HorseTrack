package com.horsetrack.equinecare.modules.tournament.service;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.tournament.dto.PrizeDTO;
import com.horsetrack.equinecare.modules.tournament.dto.RaceProposalDTO;
import com.horsetrack.equinecare.modules.tournament.dto.TournamentDTO;
import com.horsetrack.equinecare.modules.tournament.entity.CompetitionEntry;
import com.horsetrack.equinecare.modules.tournament.entity.ExternalCompetition;
import com.horsetrack.equinecare.modules.tournament.repository.CompetitionEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

/** Chuyển giải đấu / đề xuất sang DTO (kèm suất còn lại, hàng chờ, vị trí trong hàng chờ) */
@Component
@RequiredArgsConstructor
public class RaceMapper {

    public static final Map<String, String> TOURNAMENT_LABELS = Map.of(
            ExternalCompetition.DRAFT, "Nháp", ExternalCompetition.PUBLISHED, "Đã công bố", ExternalCompetition.FINISHED, "Đã kết thúc");

    private final RaceSlotAllocator slots;
    private final CompetitionEntryRepository entryRepository;

    /** Còn nhận đề xuất: đã công bố và chưa hết hạn đăng ký chung */
    public static boolean isOpen(ExternalCompetition c) {
        return ExternalCompetition.PUBLISHED.equals(c.getStatus()) && LocalDateTime.now().isBefore(c.registrationCloseAt());
    }

    public TournamentDTO toDto(ExternalCompetition c) {
        Integer id = c.getCompetitionId();
        long held = id == null ? 0 : slots.holding(id);
        int total = c.getSlots() == null ? 0 : c.getSlots();
        return TournamentDTO.builder()
                .tournamentId(id)
                .code(c.getCompetitionCode())
                .name(c.getCompetitionName())
                .organizer(c.getOrganizer())
                .location(c.getLocation())
                .raceDate(c.getStartDate())
                .registrationDeadline(c.getRegistrationDeadline())
                .distanceM(c.getRaceDistanceM())
                .entryFee(c.getStandardEntryFee())
                .entryConditions(c.getEntryConditions())
                .minAgeYears(c.getMinAgeYears())
                .maxAgeYears(c.getMaxAgeYears())
                .minWeightKg(c.getMinWeightKg())
                .maxWeightKg(c.getMaxWeightKg())
                .allowedBreeds(HorseEligibilityService.breeds(c.getAllowedBreeds()))
                .slots(c.getSlots())
                .prizes(new TreeMap<>(c.getPrizes()).entrySet().stream().map(p -> new PrizeDTO(p.getKey(), p.getValue())).toList())
                .status(c.getStatus())
                .statusLabel(TOURNAMENT_LABELS.getOrDefault(c.getStatus(), c.getStatus()))
                .publishedAt(c.getPublishedAt())
                .finished(ExternalCompetition.FINISHED.equals(c.getStatus()))
                .openForProposal(isOpen(c))
                .slotsLeft((int) Math.max(0, total - held))
                .queueLength(id == null ? 0 : (int) slots.queueLength(id))
                .hasProposals(id != null && entryRepository.existsByCompetition_CompetitionId(id))
                .build();
    }

    public RaceProposalDTO toDto(CompetitionEntry e) {
        Horse h = e.getHorse();
        ExternalCompetition c = e.getCompetition();
        boolean inStage = CompetitionEntry.IN_STAGE.contains(e.getStatus());
        return RaceProposalDTO.builder()
                .proposalId(e.getEntryId())
                .proposalCode(e.getEntryCode())
                .status(e.getStatus())
                .statusLabel(RaceNotifier.label(e.getStatus()))
                .holdingSlot(CompetitionEntry.HOLDING.contains(e.getStatus()))
                .stageDeadline(inStage ? e.getStageDeadline() : null)
                .queuePosition(slots.queuePosition(e))
                .horseId(h.getHorseId())
                .horseName(h.getHorseName())
                .ownerId(e.getOwner() != null ? e.getOwner().getUserId() : null)
                .ownerName(e.getOwner() != null ? e.getOwner().getFullName() : null)
                .trainerId(e.getTrainer() != null ? e.getTrainer().getUserId() : h.getAssignedTrainer() != null ? h.getAssignedTrainer().getUserId() : null)
                .trainerName(e.getTrainer() != null ? e.getTrainer().getFullName() : h.getAssignedTrainer() != null ? h.getAssignedTrainer().getFullName() : null)
                .tournament(toDto(c))
                .proposedAt(e.getProposedAt())
                .note(e.getProposalNote())
                .formScore(e.getFormScore())
                .slotHeldAt(e.getSlotHeldAt())
                .trainerApproved(e.getTrainerApproved())
                .trainerDecidedAt(e.getTrainerDecidedAt())
                .trainerReasonCode(e.getTrainerReasonCode())
                .trainerReasonLabel(e.getTrainerReasonCode() != null ? RaceNotifier.REJECT_REASONS.getOrDefault(e.getTrainerReasonCode(), e.getTrainerReasonCode()) : null)
                .trainerNote(e.getTrainerNote())
                .vetName(e.getVet() != null ? e.getVet().getFullName() : null)
                .vetPassed(e.getVetPassed())
                .vetCheckedAt(e.getVetCheckedAt())
                .vetNote(e.getVetNote())
                .entryFee(e.getEntryFeeCharged())
                .invoiceId(e.getInvoice() != null ? e.getInvoice().getInvoiceId() : null)
                .invoiceCode(e.getInvoice() != null ? e.getInvoice().getInvoiceCode() : null)
                .invoiceStatus(e.getInvoice() != null ? e.getInvoice().getPaymentStatus() : null)
                .paidAt(e.getPaidAt())
                .complaintStatus(e.getComplaintStatus())
                .complaintNote(e.getComplaintNote())
                .complaintAt(e.getComplaintAt())
                .complaintDecisionNote(e.getComplaintDecisionNote())
                .complaintDecidedAt(e.getComplaintDecidedAt())
                .canComplain(CompetitionEntry.TRAINER_REJECTED.equals(e.getStatus()) && e.getComplaintStatus() == null && isOpen(c))
                .closedReason(e.getClosedReason())
                .closedAt(e.getClosedAt())
                .rank(e.getRankPosition())
                .finishTimeSeconds(e.getFinishTimeSeconds())
                .prizeAmount(CompetitionEntry.RESULT_RECORDED.equals(e.getStatus()) ? e.getPrizeAwarded() : null)
                .resultNote(e.getResultNote())
                .build();
    }
}
