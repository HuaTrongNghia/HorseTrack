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
                ai đoạn sớm nhất còn hóa đơn mở (FE hiển thị đúng "chờ thanh toán giai đoạn N", không cố định giai đoạn 1)
        

        
      

    // Tự tính thay vì đọc cột line_total (cột tính toán chỉ có sau khi đọc lại từ DB)
   
}
