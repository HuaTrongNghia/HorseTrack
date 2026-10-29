package com.horsetrack.equinecare.modules.medical.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.entity.GroomProfile;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.GroomProfileRepository;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.medical.dto.CaseReportRequestDTO;
import com.horsetrack.equinecare.modules.medical.dto.MedicalCaseDTO;
import com.horsetrack.equinecare.modules.medical.dto.VetActionRequestDTO;
import com.horsetrack.equinecare.modules.medical.dto.VetLockRequestDTO;
import com.horsetrack.equinecare.modules.medical.entity.VetCase;
import com.horsetrack.equinecare.modules.medical.entity.TreatmentProtocol;
import com.horsetrack.equinecare.modules.medical.repository.TreatmentProtocolRepository;
import com.horsetrack.equinecare.modules.medical.repository.VetCaseRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalService {

    private final HorseRepository horseRepository;
    private static final Set<String> PROTOCOL_FINISHED = Set.of("COMPLETED", "CANCELLED", "SUPERSEDED");

    private final VetCaseRepository vetCaseRepository;
    private final TreatmentProtocolRepository protocolRepository;
    private final UserRepository userRepository;
    private final GroomProfileRepository groomProfileRepository;
    private final EntityManager entityManager;
    private final com.horsetrack.equinecare.modules.training.service.TrainingLockHandler trainingLockHandler;
    private final com.horsetrack.equinecare.modules.audit.service.AuditTrail auditTrail;
    private final com.horsetrack.equinecare.modules.horse.service.HorseAccessGuard horseAccessGuard;
    private final com.horsetrack.equinecare.modules.training.service.SessionIncidentHandler incidentHandler;
    private final HorseConditionNotifier conditionNotifier;

    private boolean isCurrentUserVeterinarian() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "Veterinarian".equals(a.getAuthority()));
    }

   
    @Transactional
    public void applyVetLock(VetLockRequestDTO dto) throws Exception {
        Horse horse = horseRepository.findById(dto.getHorseId())
                .orElseThrow(() -> new Exception("Không tìm thấy ngựa với ID: " + dto.getHorseId()));

        if (Boolean.TRUE.equals(horse.getIsLocked())) {
            throw new Exception("Ngựa này hiện đang bị khóa y tế rồi!");
        }

        horse.setIsLocked(true);

        // Chỉ Bác sĩ thú y khóa huấn luyện (MedicalController), kết luận ngựa chấn thương
        horse.setHealthStatus("INJURED");

        horseRepository.save(horse);
        conditionNotifier.onStatus(horse, "INJURED", dto.getReason());

        int cancelled = trainingLockHandler.onHorseLocked(horse, dto.getReason());
        auditTrail.record("VET_LOCK", "Horses", "isLocked=false",
                "isLocked=true, health=" + horse.getHealthStatus() + " | ngựa " + horse.getHorseName() + " (horse_id=" + horse.getHorseId()
                        + ") | hủy " + cancelled + " buổi tập | Lý do: " + dto.getReason());

        log.warn("EMERGENCY VETLOCK: Ngựa ID {} đã bị khóa bởi {} ({})! Lý do: {}", horse.getHorseId(),
                SecurityContextHolder.getContext().getAuthentication().getName(), horse.getHealthStatus(), dto.getReason());
    }

    // 2. Hàm mở khóa y tế (Hồi phục)
    @Transactional
    public void releaseVetLock(Integer horseId) throws Exception {
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new Exception("Không tìm thấy ngựa!"));

        if (!Boolean.TRUE.equals(horse.getIsLocked())) {
            throw new Exception("Ngựa này hiện không bị khóa y tế, không cần mở khóa!");
        }

        horse.setIsLocked(false);

        // Đặt lại health_status về trạng thái khỏe mạnh hợp lệ của DB (Ví dụ: "Healthy" hoặc "Khỏe mạnh")
        horse.setHealthStatus("HEALTHY");

        horseRepository.save(horse);
        trainingLockHandler.onHorseUnlocked(horse);
        auditTrail.record("VET_UNLOCK", "Horses", "isLocked=true",
                "isLocked=false, health=HEALTHY | ngựa " + horse.getHorseName() + " (horse_id=" + horse.getHorseId() + ")");
    }

    // ==========================================
    // CA KHÁM / SỰ CỐ (bảng Vet_Cases)
    // ==========================================

    /**
     * Hộp thư ca khám. Bác sĩ thú y xem mọi ca; Groom / Trainer chỉ xem các ca do chính mình báo.
     * status = ALL (hoặc bỏ trống) để lấy mọi trạng thái.
     */
    @Transactional(readOnly = true)
    public List<MedicalCaseDTO> getInboxCases(String status) {
        String st = StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status.trim()) ? status.trim().toUpperCase() : null;
        Integer reportedById = isCurrentUserVeterinarian() ? null : currentUser().getUserId();
        return vetCaseRepository.search(st, reportedById).stream().map(this::toCaseDTO).collect(Collectors.toList());
    }

    /** Groom / Trainer báo sự cố; Groom chỉ báo được cho ngựa thuộc khu mình phụ trách (SRS màn hình 51). */
    @Transactional
    public MedicalCaseDTO reportCase(CaseReportRequestDTO dto) {
        User reporter = currentUser();
        Horse horse = horseRepository.findById(dto.getHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + dto.getHorseId()));

        // Trainer chỉ báo sự cố cho ngựa được phân công cho mình
        horseAccessGuard.checkAssigned(horse);
        boolean isGroom = "Groom".equals(reporter.getRoleName());
        if (isGroom) {
            String horseZone = horse.getStall() != null ? horse.getStall().getBarnZone() : null;
            String groomZone = groomProfileRepository.findById(reporter.getUserId()).map(GroomProfile::getAssignedBarnZone).orElse(null);
            if (horseZone == null || groomZone == null || !horseZone.trim().equalsIgnoreCase(groomZone.trim())) {
                throw new AccessDeniedException("Bạn chỉ báo sự cố được cho ngựa thuộc khu mình phụ trách (khu của bạn: "
                        + (groomZone == null ? "chưa có" : groomZone) + ").");
            }
        }

        VetCase vetCase = VetCase.builder()
                .horse(horse)
                .reportedBy(reporter)
                .caseType(dto.getCaseType().trim())
                .description(dto.getDescription().trim())
                .severity(StringUtils.hasText(dto.getSeverity()) ? dto.getSeverity().toUpperCase() : "MEDIUM")
                .source(isGroom ? "GROOM_REPORT" : "TRAINER_REQUEST")
                .status("NEW")
                .reportedAt(LocalDateTime.now())
                .build();
        VetCase saved = vetCaseRepository.saveAndFlush(vetCase);
        // Đọc lại để lấy case_code do DB tự sinh
        entityManager.refresh(saved);
        log.info("Ca khám mới {} cho ngựa {} do {} báo", saved.getCaseCode(), horse.getHorseName(), reporter.getUsername());
        return toCaseDTO(saved);
    }

    /** Bác sĩ ghi nhận kết quả khám và hướng xử lý: ca chuyển sang MONITORING, gán cho bác sĩ đang đăng nhập. */
    @Transactional
    public MedicalCaseDTO processVetAction(VetActionRequestDTO dto) {
        VetCase vetCase = findCase(dto.getCaseId());
        if ("CLOSED".equals(vetCase.getStatus()) || "CANCELLED".equals(vetCase.getStatus())) {
            throw new IllegalArgumentException("Ca " + vetCase.getCaseCode() + " đã đóng, không thể ghi nhận thêm.");
        }
        String result = "Kết quả khám: " + dto.getVetNotes().trim()
                + (StringUtils.hasText(dto.getTreatmentDirection()) ? " | Hướng xử lý: " + dto.getTreatmentDirection().trim() : "");
        vetCase.setOutcome(result.length() > 1000 ? result.substring(0, 1000) : result);
        vetCase.setAssignedVet(currentUser());
        vetCase.setStatus("MONITORING");
        return toCaseDTO(vetCaseRepository.save(vetCase));
    }

    /** Bác sĩ đóng ca khi không cần điều trị thêm (SRS màn hình 60). */
    @Transactional
    public MedicalCaseDTO closeCase(String caseRef, String outcome) {
        VetCase vetCase = findCase(caseRef);
        if ("CLOSED".equals(vetCase.getStatus()) || "CANCELLED".equals(vetCase.getStatus())) {
            throw new IllegalArgumentException("Ca " + vetCase.getCaseCode() + " đã đóng trước đó.");
        }
        TreatmentProtocol openProtocol = protocolRepository.findByCaseId(vetCase.getCaseId()).stream()
                .filter(p -> !PROTOCOL_FINISHED.contains(p.getState()))
                .findFirst()
                .orElse(null);
        if (openProtocol != null) {
            throw new IllegalArgumentException("Phác đồ " + openProtocol.getProtocolCode() + " của ca " + vetCase.getCaseCode()
                    + " chưa hoàn thành (đang " + openProtocol.getState() + "). Hãy kết thúc hoặc hủy phác đồ trước khi đóng ca.");
        }
        vetCase.setStatus("CLOSED");
        vetCase.setOutcome(outcome.trim().length() > 1000 ? outcome.trim().substring(0, 1000) : outcome.trim());
        vetCase.setAssignedVet(vetCase.getAssignedVet() != null ? vetCase.getAssignedVet() : currentUser());
        vetCase.setClosedAt(LocalDateTime.now());
        VetCase saved = vetCaseRepository.save(vetCase);
        // Bác sĩ đóng ca mà không khóa huấn luyện: chỉ kết thúc buổi tập đang chờ, giáo án chạy tiếp
        Horse horse = saved.getHorse();
        if (horse != null && !Boolean.TRUE.equals(horse.getIsLocked())) {
            incidentHandler.resolveWaitingVet(horse, false, outcome);
        }
        return toCaseDTO(saved);
    }

    // Nhận mã ca (C-0001) hoặc ID số
    private VetCase findCase(String caseRef) {
        String ref = caseRef == null ? "" : caseRef.trim();
        if (ref.startsWith("#")) {
            ref = ref.substring(1);
        }
        Optional<VetCase> found = vetCaseRepository.findByCaseCode(ref.toUpperCase());
        if (found.isEmpty() && ref.matches("\\d+")) {
            found = vetCaseRepository.findById(Integer.valueOf(ref));
        }
        String shown = ref;
        return found.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca khám: " + shown));
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }

    private MedicalCaseDTO toCaseDTO(VetCase c) {
        return MedicalCaseDTO.builder()
                .caseId(c.getCaseCode())
                .id(c.getCaseId())
                .horseId(c.getHorse().getHorseId())
                .horseName(c.getHorse().getHorseName())
                .source(c.getSource())
                .issueType(c.getCaseType())
                .severity(c.getSeverity())
                .status(c.getStatus())
                .description(c.getDescription())
                .reportedById(c.getReportedBy().getUserId())
                .reportedByName(c.getReportedBy().getFullName())
                .assignedVetName(c.getAssignedVet() != null ? c.getAssignedVet().getFullName() : null)
                .vetResult(c.getOutcome())
                .createdAt(c.getReportedAt())
                .closedAt(c.getClosedAt())
                .build();
    }
}
