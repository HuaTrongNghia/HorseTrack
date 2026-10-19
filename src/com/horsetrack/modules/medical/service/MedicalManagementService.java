package com.horsetrack.service;

import com.horsetrack.dto.TreatmentProtocolCreateDTO;
import com.horsetrack.entity.*;
import com.horsetrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MedicalManagementService {

    private final MedicationRepository medicationRepository;
    private final TreatmentProtocolRepository protocolRepository;
    private final HorseRepository horseRepository;

    public List<Medication> getAllMedications() {
        return medicationRepository.findAll();
    }

    @Transactional
    public TreatmentProtocol createProtocol(TreatmentProtocolCreateDTO dto) throws Exception {
        Horse horse = horseRepository.findById(dto.getHorseId())
                .orElseThrow(() -> new Exception("Không tìm thấy thông tin ngựa!"));

        TreatmentProtocol protocol = new TreatmentProtocol();
        protocol.setProtocolCode("PD-" + System.currentTimeMillis() % 10000);
        protocol.setHorse(horse);
        protocol.setMedicalCaseId(dto.getCaseId());
        protocol.setDiagnosis(dto.getDiagnosis());
        protocol.setTargetDate(dto.getTargetDate());
        protocol.setStatus("Đang điều trị");

        BigDecimal totalCost = BigDecimal.ZERO;
        List<TreatmentStage> stageList = new ArrayList<>();

        if (dto.getStages() != null) {
            for (var sDto : dto.getStages()) {
                TreatmentStage stage = new TreatmentStage();
                stage.setProtocol(protocol);
                stage.setStageName(sDto.getStageName());
                stage.setStartDate(sDto.getStartDate());
                stage.setDurationDays(sDto.getDurationDays());
                stage.setGoal(sDto.getGoal());

                List<Prescription> prescriptions = new ArrayList<>();
                if (sDto.getItems() != null) {
                    for (var iDto : sDto.getItems()) {
                        Medication med = medicationRepository.findById(iDto.getMedicationId())
                                .orElseThrow(() -> new Exception("Không tìm thấy thuốc ID: " + iDto.getMedicationId()));

                        if (med.getStockAvailable() < iDto.getQuantity()) {
                            throw new Exception("Thuốc " + med.getName() + " không đủ tồn kho khả dụng!");
                        }

                        // Trừ tồn kho
                        med.setStockAvailable(med.getStockAvailable() - iDto.getQuantity());
                        medicationRepository.save(med);

                        Prescription p = new Prescription();
                        p.setStage(stage);
                        p.setMedication(med);
                        p.setQuantity(iDto.getQuantity());
                        
                        BigDecimal lineTotal = med.getPrice().multiply(BigDecimal.valueOf(iDto.getQuantity()));
                        p.setLineTotal(lineTotal);
                        totalCost = totalCost.add(lineTotal);

                        prescriptions.add(p);
                    }
                }
                stage.setPrescriptions(prescriptions);
                stageList.add(stage);
            }
        }

        protocol.setStages(stageList);
        protocol.setTotalCost(totalCost);

        return protocolRepository.save(protocol);
    }
}
