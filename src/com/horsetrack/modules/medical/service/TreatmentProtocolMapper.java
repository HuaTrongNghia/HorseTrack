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

        
      

    // Tự tính thay vì đọc cột line_total (cột tính toán chỉ có sau khi đọc lại từ DB)
   
}
