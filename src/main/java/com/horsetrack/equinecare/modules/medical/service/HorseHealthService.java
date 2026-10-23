package com.horsetrack.equinecare.modules.medical.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.medical.dto.HealthStatusUpdateDTO;
import com.horsetrack.equinecare.modules.medical.dto.InjuryCreateDTO;
import com.horsetrack.equinecare.modules.medical.dto.InjuryDTO;
import com.horsetrack.equinecare.modules.medical.entity.InjuryMark;
import com.horsetrack.equinecare.modules.medical.entity.VetCase;
import com.horsetrack.equinecare.modules.medical.repository.InjuryMarkRepository;
import com.horsetrack.equinecare.modules.medical.repository.VetCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Hồ sơ sức khỏe của ngựa: đánh dấu chấn thương trên sơ đồ 2D và trạng thái sức khỏe (bác sĩ thú y) */
@Service
@RequiredArgsConstructor
public class HorseHealthService {

    private static final Map<String, String> SEVERITY = Map.of(
            "MINOR", "MINOR", "NHẸ", "MINOR", "NHE", "MINOR",
            "MODERATE", "MODERATE", "TRUNG BÌNH", "MODERATE", "TRUNG BINH", "MODERATE",
            "SEVERE", "SEVERE", "NẶNG", "SEVERE", "NANG", "SEVERE");
    private static final Map<String, String> SEVERITY_LABEL = Map.of("MINOR", "Nhẹ", "MODERATE", "Trung bình", "SEVERE", "Nặng");
    private static final Map<String, String> HEALTH = Map.of(
            "HEALTHY", "HEALTHY", "KHỎE MẠNH", "HEALTHY",
            "WATCH", "WATCH", "THEO DÕI", "WATCH",
            "INJURED", "INJURED", "CHẤN THƯƠNG", "INJURED",
            "QUARANTINE", "QUARANTINE", "CÁCH LY", "QUARANTINE");

    private final InjuryMarkRepository injuryRepository;
    private final HorseRepository horseRepository;
    private final VetCaseRepository vetCaseRepository;
    private final UserRepository userRepository;
    private final AuditTrail auditTrail;

    @Transactional(readOnly = true)
    public List<InjuryDTO> listForHorse(Integer horseId) {
        return injuryRepository.findByHorse_HorseIdOrderByMarkIdDesc(horseId).stream().map(HorseHealthService::toDto).toList();
    }

    @Transactional
    public InjuryDTO addInjury(Integer horseId, InjuryCreateDTO request) {
        Horse horse = findHorse(horseId);
        String severity = SEVERITY.get(request.getSeverity().trim().toUpperCase());
        if (severity == null) {
            throw new IllegalArgumentException("Mức độ '" + request.getSeverity() + "' không hợp lệ (Nhẹ / Trung bình / Nặng hoặc MINOR / MODERATE / SEVERE).");
        }
        VetCase vetCase = null;
        if (StringUtils.hasText(request.getCaseId())) {
            String ref = request.getCaseId().trim();
            vetCase = (ref.matches("\\d+") ? vetCaseRepository.findById(Integer.valueOf(ref)) : vetCaseRepository.findByCaseCode(ref))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca khám " + ref));
            if (vetCase.getHorse() == null || !vetCase.getHorse().getHorseId().equals(horse.getHorseId())) {
                throw new IllegalArgumentException("Ca khám " + ref + " không thuộc ngựa " + horse.getHorseName() + ".");
            }
        }
        InjuryMark mark = injuryRepository.save(InjuryMark.builder()
                .horse(horse)
                .vetCase(vetCase)
                .affectedBodyPart(request.getPart().trim())
                .severityLevel(severity)
                .notes(StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null)
                .coordX(request.getX())
                .coordY(request.getY())
                .createdBy(currentUser())
                .createdAt(LocalDateTime.now())
                .build());
        auditTrail.record("MARK_INJURY", "Injury_Marks", null,
                "chấn thương #" + mark.getMarkId() + " | ngựa " + horse.getHorseName() + " | " + mark.getAffectedBodyPart() + " | " + severity);
        return toDto(mark);
    }

    @Transactional
    public void deleteInjury(Integer horseId, Integer injuryId) {
        InjuryMark mark = injuryRepository.findById(injuryId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chấn thương với ID: " + injuryId));
        if (mark.getHorse() == null || !mark.getHorse().getHorseId().equals(horseId)) {
            throw new ResourceNotFoundException("Ngựa #" + horseId + " không có chấn thương #" + injuryId);
        }
        if (mark.getMedicalRecord() != null) {
            throw new IllegalArgumentException("Chấn thương này thuộc bệnh án #" + mark.getMedicalRecord().getRecordId() + ", không gỡ riêng được.");
        }
        injuryRepository.delete(mark);
        auditTrail.record("REMOVE_INJURY", "Injury_Marks",
                "chấn thương #" + injuryId + " | " + mark.getAffectedBodyPart() + " | " + mark.getSeverityLevel(),
                "đã gỡ | ngựa " + mark.getHorse().getHorseName());
    }

    @Transactional
    public String updateHealthStatus(Integer horseId, HealthStatusUpdateDTO request) {
        Horse horse = findHorse(horseId);
        String status = HEALTH.get(request.getStatus().trim().toUpperCase());
        if (status == null) {
            throw new IllegalArgumentException("Trạng thái '" + request.getStatus() + "' không hợp lệ (HEALTHY / WATCH / INJURED / QUARANTINE).");
        }
        String before = horse.getHealthStatus();
        if (status.equalsIgnoreCase(before)) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " đã ở trạng thái " + status + ".");
        }
        horse.setHealthStatus(status);
        horseRepository.save(horse);
        auditTrail.record("UPDATE_HEALTH_STATUS", "Horses", "health_status=" + before,
                "health_status=" + status + " | ngựa " + horse.getHorseName() + " | " + request.getReason().trim());
        return status;
    }

    public static InjuryDTO toDto(InjuryMark m) {
        return InjuryDTO.builder()
                .injuryId(m.getMarkId())
                .part(m.getAffectedBodyPart())
                .severity(m.getSeverityLevel())
                .severityLabel(SEVERITY_LABEL.getOrDefault(m.getSeverityLevel(), m.getSeverityLevel()))
                .note(m.getNotes())
                .x(m.getCoordX())
                .y(m.getCoordY())
                .caseId(m.getVetCase() != null ? m.getVetCase().getCaseId() : null)
                .caseCode(m.getVetCase() != null ? m.getVetCase().getCaseCode() : null)
                .recordId(m.getMedicalRecord() != null ? m.getMedicalRecord().getRecordId() : null)
                .createdByName(m.getCreatedBy() != null ? m.getCreatedBy().getFullName() : null)
                .createdAt(m.getCreatedAt())
                .build();
    }

    private Horse findHorse(Integer horseId) {
        return horseRepository.findById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + horseId));
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }
}
