package com.horsetrack.equinecare.modules.medical.service;

import com.horsetrack.equinecare.modules.inventory.entity.StockHold;
import com.horsetrack.equinecare.modules.inventory.repository.StockHoldRepository;
import com.horsetrack.equinecare.modules.medical.dto.TreatmentProtocolDTO;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolItem;
import com.horsetrack.equinecare.modules.medical.entity.TreatmentProtocol;
import com.horsetrack.equinecare.modules.medical.repository.ProtocolProgressNoteRepository;
import com.horsetrack.equinecare.modules.medical.repository.TreatmentProtocolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/** Chuyển phác đồ sang DTO; dùng chung cho API của bác sĩ và của kho. */
@Component
@RequiredArgsConstructor
public class TreatmentProtocolMapper {

    private final TreatmentProtocolRepository protocolRepository;
    private final StockHoldRepository holdRepository;
    private final ProtocolProgressNoteRepository noteRepository;
    private final com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling phaseBilling;

    public TreatmentProtocolDTO toDTO(TreatmentProtocol p, boolean detail) {
        TreatmentProtocol replacedBy = protocolRepository.findByReplacesProtocol_ProtocolId(p.getProtocolId()).orElse(null);

        TreatmentProtocolDTO dto = TreatmentProtocolDTO.builder()
                .protocolId(p.getProtocolId())
                .protocolCode(p.getProtocolCode())
                .horseId(p.getHorse().getHorseId())
                .horseName(p.getHorse().getHorseName())
                .vetId(p.getVet().getUserId())
                .vetName(p.getVet().getFullName())
                .caseId(p.getCaseId())
                .recordId(p.getRecordId())
                .versionNo(p.getVersionNo())
                .replacesProtocolId(p.getReplacesProtocol() != null ? p.getReplacesProtocol().getProtocolId() : null)
                .replacesProtocolCode(p.getReplacesProtocol() != null ? p.getReplacesProtocol().getProtocolCode() : null)
                .replacedByProtocolId(replacedBy != null ? replacedBy.getProtocolId() : null)
                .replacedByProtocolCode(replacedBy != null ? replacedBy.getProtocolCode() : null)
                .diagnosis(p.getDiagnosis())
                .state(p.getState())
                .requiredBy(p.getRequiredBy())
                .isDelayed(p.getIsDelayed())
                .trainingLocked(p.getTrainingLocked())
                .waitEta(p.getWaitEta())
                .waitNote(p.getWaitNote())
                .note(p.getNote())
                .outcome(p.getOutcome())
                .createdAt(p.getCreatedAt())
                .confirmedAt(p.getConfirmedAt())
                .closedAt(p.getClosedAt())
                .totalCost(totalCost(p))
                .emergency(phaseBilling.isEmergency(p))
                .pendingSupplement("IN_TREATMENT".equals(p.getState())
                        && !holdRepository.findByProtocolIdAndStatusIn(p.getProtocolId(), List.of("HELD", "SHORT")).isEmpty())
                .build();

        if (detail) {
            dto.setPhases(p.getPhases().stream().map(ph -> TreatmentProtocolDTO.Phase.builder()
                    .phaseId(ph.getPhaseId())
                    .phaseNo(ph.getPhaseNo())
                    .phaseName(ph.getPhaseName())
                    .startDate(ph.getStartDate())
                    .durationDays(ph.getDurationDays())
                    .goal(ph.getGoal())
                    .invoiceId(phaseBilling.findPhaseInvoice(p.getProtocolId(), ph.getPhaseNo()).map(i -> i.getInvoiceId()).orElse(null))
                    .invoiceCode(phaseBilling.findPhaseInvoice(p.getProtocolId(), ph.getPhaseNo()).map(i -> i.getInvoiceCode()).orElse(null))
                    .invoiceStatus(phaseBilling.findPhaseInvoice(p.getProtocolId(), ph.getPhaseNo()).map(i -> i.getPaymentStatus()).orElse(null))
                    .items(ph.getItems().stream().map(it -> TreatmentProtocolDTO.Item.builder()
                            .protocolItemId(it.getProtocolItemId())
                            .itemId(it.getItem().getItemId())
                            .itemCode(it.getItem().getItemCode())
                            .itemName(it.getItem().getItemName())
                            .unit(it.getItem().getUnit())
                            .quantity(it.getQuantity())
                            .unitPrice(it.getUnitPrice())
                            .lineTotal(lineTotal(it))
                            .dosage(it.getDosage())
                            .build()).collect(Collectors.toList()))
                    .build()).collect(Collectors.toList()));

            dto.setStockAnswer(holdRepository.findByProtocolIdOrderByHoldIdAsc(p.getProtocolId()).stream()
                    .map(TreatmentProtocolMapper::toStockAnswer)
                    .collect(Collectors.toList()));

            dto.setProgressNotes(noteRepository.findByProtocol_ProtocolIdOrderByWrittenAtAsc(p.getProtocolId()).stream()
                    .map(n -> TreatmentProtocolDTO.Note.builder()
                            .noteId(n.getNoteId())
                            .writtenByName(n.getWrittenBy().getFullName())
                            .writtenAt(n.getWrittenAt())
                            .content(n.getContent())
                            .build())
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    public List<TreatmentProtocolDTO> toSummaryList(List<TreatmentProtocol> protocols) {
        return protocols.stream().map(p -> toDTO(p, false)).collect(Collectors.toList());
    }

    private static TreatmentProtocolDTO.StockAnswer toStockAnswer(StockHold h) {
        double needed = h.getQtyNeeded() != null ? h.getQtyNeeded() : 0;
        double held = h.getQtyHeld() != null ? h.getQtyHeld() : 0;
        return TreatmentProtocolDTO.StockAnswer.builder()
                .holdId(h.getHoldId())
                .itemId(h.getItem().getItemId())
                .itemName(h.getItem().getItemName())
                .unit(h.getItem().getUnit())
                .qtyNeeded(h.getQtyNeeded())
                .qtyAvailable(h.getQtyAvailable())
                .qtyHeld(h.getQtyHeld())
                .qtyShort(Math.max(0, needed - held))
                .eta(h.getEta())
                .purchaseOrderId(h.getPurchaseOrderId())
                .status(h.getStatus())
                .checkedByName(h.getCheckedBy() != null ? h.getCheckedBy().getFullName() : null)
                .checkedAt(h.getCheckedAt())
                .build();
    }

    // Tự tính thay vì đọc cột line_total (cột tính toán chỉ có sau khi đọc lại từ DB)
    private static BigDecimal lineTotal(ProtocolItem it) {
        BigDecimal price = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
        return price.multiply(BigDecimal.valueOf(it.getQuantity())).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal totalCost(TreatmentProtocol p) {
        return p.getPhases().stream()
                .flatMap(ph -> ph.getItems().stream())
                .map(TreatmentProtocolMapper::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
