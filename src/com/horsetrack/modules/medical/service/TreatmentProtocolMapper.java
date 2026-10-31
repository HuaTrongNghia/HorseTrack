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
    private final com.horsetrack.equinecare.modules.inventory.service.DispensingHistory dispensingHistory;

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

        // Đang chờ thanh toán: giai đoạn sớm nhất còn hóa đơn mở (FE hiển thị đúng "chờ thanh toán giai đoạn N", không cố định giai đoạn 1)
        if (com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling.ON_HOLD.equals(p.getState())) {
            p.getPhases().stream()
                    .filter(ph -> ph.getPhaseNo() != null)
                    .sorted(java.util.Comparator.comparing(com.horsetrack.equinecare.modules.medical.entity.ProtocolPhase::getPhaseNo))
                    .filter(ph -> phaseBilling.findPhaseInvoice(p.getProtocolId(), ph.getPhaseNo()).filter(i -> i.isOpen()).isPresent())
                    .findFirst()
                    .ifPresent(ph -> {
                        dto.setAwaitingPaymentPhaseNo(ph.getPhaseNo());
                        dto.setAwaitingPaymentPhaseName(ph.getPhaseName());
                        dto.setAwaitingPaymentInvoiceCode(phaseBilling.findPhaseInvoice(p.getProtocolId(), ph.getPhaseNo()).map(i -> i.getInvoiceCode()).orElse(null));
                    });
        }

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

            dto.setPhaseProgress(phaseProgress(p));

            var issues = dispensingHistory.byHold(p.getProtocolId());
            dto.setStockAnswer(holdRepository.findByProtocolIdOrderByHoldIdAsc(p.getProtocolId()).stream()
                    .map(h -> {
                        TreatmentProtocolDTO.StockAnswer a = toStockAnswer(h);
                        var lines = issues.get(h.getHoldId());
                        if (lines != null && !lines.isEmpty()) {
                            a.setIssuedAt(lines.get(0).getIssuedAt());
                            a.setIssuedByName(lines.get(0).getIssuedByName());
                            a.setIssuedLots(lines.stream().map(com.horsetrack.equinecare.modules.inventory.service.DispensingHistory.Record::lotLabel).toList());
                        }
                        return a;
                    })
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

    /**
     * Trạng thái từng giai đoạn, áp dụng như nhau cho mọi giai đoạn (không chỉ giai đoạn 1):
     * giai đoạn đầu tiên chưa hoàn thành là "đang thực hiện" khi phác đồ đang điều trị và giai đoạn đó đã bắt đầu
     * (giai đoạn đầu: từ lúc vào điều trị; giai đoạn sau: có ghi chú "Bắt đầu (sớm) giai đoạn N").
     */
    private List<TreatmentProtocolDTO.PhaseProgress> phaseProgress(TreatmentProtocol p) {
        java.util.Set<Integer> finished = phaseBilling.finishedPhaseNos(p.getProtocolId());
        boolean closed = java.util.Set.of("COMPLETED", "CANCELLED", "SUPERSEDED").contains(p.getState());
        boolean inTreatment = "IN_TREATMENT".equals(p.getState());
        boolean running = inTreatment || com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling.ON_HOLD.equals(p.getState());
        List<com.horsetrack.equinecare.modules.medical.entity.ProtocolPhase> phases = p.getPhases().stream()
                .filter(ph -> ph.getPhaseNo() != null)
                .sorted(java.util.Comparator.comparing(com.horsetrack.equinecare.modules.medical.entity.ProtocolPhase::getPhaseNo))
                .toList();
        List<TreatmentProtocolDTO.PhaseProgress> out = new java.util.ArrayList<>();
        boolean earlierDone = true;
        for (int i = 0; i < phases.size(); i++) {
            var ph = phases.get(i);
            int no = ph.getPhaseNo();
            var row = TreatmentProtocolDTO.PhaseProgress.builder().phaseNo(no).canFinish(false).canStart(false);
            if (finished.contains(no)) {
                out.add(row.status("COMPLETED").build());
                continue;
            }
            if (closed) {
                out.add(row.status("STOPPED").build());
                earlierDone = false;
                continue;
            }
            boolean started = (i == 0 && inTreatment) || phaseBilling.isPhaseStarted(p.getProtocolId(), no);
            if (earlierDone && running && started) {
                String blocked = null;
                try {
                    phaseBilling.assertCanFinishPhase(p, no);
                } catch (IllegalArgumentException ex) {
                    blocked = ex.getMessage();
                }
                out.add(row.status("IN_PROGRESS").canFinish(blocked == null).finishBlockedReason(blocked).build());
            } else if (phaseBilling.isPhaseSettled(p, no)) {
                out.add(row.status("READY_TO_START").canStart(earlierDone && running && i > 0).build());
            } else {
                out.add(row.status("WAITING_PAYMENT").build());
            }
            earlierDone = false;
        }
        return out;
    }

    public List<TreatmentProtocolDTO> toSummaryList(List<TreatmentProtocol> protocols) {
        return protocols.stream().map(p -> toDTO(p, true)).collect(Collectors.toList());
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
