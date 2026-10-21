package com.horsetrack.equinecare.modules.horse.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.dto.*;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.entity.HorseRegistration;
import com.horsetrack.equinecare.modules.horse.entity.RegistrationCheck;
import com.horsetrack.equinecare.modules.horse.entity.Stall;
import com.horsetrack.equinecare.modules.horse.repository.HorseRegistrationRepository;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.horse.repository.StallRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class HorseServiceImpl implements HorseService {

    private static final String ROLE_OWNER = "Horse Owner";
    private static final String ROLE_VET = "Veterinarian";
    // 3 mục kiểm tra bắt buộc của hồ sơ (Registration_Checks.check_key)
    private static final Set<String> REQUIRED_CHECKS = Set.of("IDENTITY", "HEALTH", "OWNERSHIP");

    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final StallRepository stallRepository;
    private final HorseRegistrationRepository registrationRepository;
    private final EntityManager entityManager;

    public HorseServiceImpl(HorseRepository horseRepository, UserRepository userRepository, StallRepository stallRepository,
                            HorseRegistrationRepository registrationRepository, EntityManager entityManager) {
        this.horseRepository = horseRepository;
        this.userRepository = userRepository;
        this.stallRepository = stallRepository;
        this.registrationRepository = registrationRepository;
        this.entityManager = entityManager;
    }

    private boolean currentUserHasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }

    // Chủ ngựa chỉ được thao tác trên dữ liệu của chính mình; nhân viên CLB không bị giới hạn
    private void checkOwnerAccess(Integer ownerId, String message) {
        if (currentUserHasRole(ROLE_OWNER) && !currentUser().getUserId().equals(ownerId)) {
            throw new AccessDeniedException(message);
        }
    }

    private Horse findHorse(Integer horseId) {
        return horseRepository.findById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + horseId));
    }

    private HorseResponseDTO mapToResponseDTO(Horse horse) {
        HorseRegistration latest = registrationRepository.findFirstByHorse_HorseIdOrderByRegistrationIdDesc(horse.getHorseId()).orElse(null);
        if (latest != null && latest.getRegistrationCode() == null) {
            // Hồ sơ vừa tạo trong cùng transaction: đọc lại để lấy registration_code do DB tự sinh
            entityManager.refresh(latest);
        }
        boolean rejected = latest != null && "REJECTED".equals(latest.getStatus());
        return HorseResponseDTO.builder()
                .horseId(horse.getHorseId())
                .horseName(horse.getHorseName())
                .dob(horse.getDob())
                .gender(horse.getGender())
                .breed(horse.getBreed())
                .pedigree(horse.getPedigree())
                .weightKg(horse.getWeightKg())
                .healthStatus(horse.getHealthStatus())
                .isLocked(horse.getIsLocked())
                .prizeSharePct(horse.getPrizeSharePct())
                .ownerId(horse.getOwner().getUserId())
                .stallId(horse.getStall() != null ? horse.getStall().getStallId() : null)
                .stallCode(horse.getStall() != null ? horse.getStall().getStallCode() : null)
                .approvalStatus(horse.getApprovalStatus())
                .createdAt(horse.getCreatedAt())
                .horseCode(horse.getHorseCode())
                .color(horse.getColor())
                .heightHands(horse.getHeightHands())
                .passportNo(horse.getPassportNo())
                .healthCertIssuer(horse.getHealthCertIssuer())
                .healthCertExpiry(horse.getHealthCertExpiry())
                .registrationId(latest != null ? latest.getRegistrationId() : null)
                .registrationCode(latest != null ? latest.getRegistrationCode() : null)
                .rejectReason(rejected ? latest.getDecisionNote() : null)
                .allowResubmit(rejected ? latest.getAllowResubmit() : null)
                .build();
    }

    // ==========================================
    // XEM NGỰA
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public List<HorseResponseDTO> getMyHorses(Integer ownerId) {
        checkOwnerAccess(ownerId, "Bạn chỉ được xem danh sách ngựa của chính mình");
        if (!userRepository.existsById(ownerId)) {
            throw new ResourceNotFoundException("Không tìm thấy chủ ngựa với ID: " + ownerId);
        }
        return horseRepository.findByOwnerUserId(ownerId).stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HorseResponseDTO> getAllHorses(int page, int size, String status) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Horse> horsePage;
        if ("PENDING".equalsIgnoreCase(status)) {
            horsePage = horseRepository.findPendingApproval(pageable);
        } else if ("APPROVED".equalsIgnoreCase(status)) {
            horsePage = horseRepository.findApproved(pageable);
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            horsePage = horseRepository.findRejected(pageable);
        } else {
            horsePage = horseRepository.findAll(pageable);
        }
        return horsePage.map(this::mapToResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public HorseResponseDTO getHorseDetail(Integer horseId) {
        Horse horse = findHorse(horseId);
        checkOwnerAccess(horse.getOwner().getUserId(), "Bạn không phải chủ của ngựa này");
        return mapToResponseDTO(horse);
    }

    // ==========================================
    // HỒ SƠ ĐĂNG KÝ: NỘP, DUYỆT, TỪ CHỐI, NỘP LẠI
    // ==========================================

    @Override
    @Transactional
    public HorseResponseDTO registerHorse(HorseRegistrationRequestDTO request) {
        checkOwnerAccess(request.getOwnerId(), "Chủ ngựa chỉ được đăng ký ngựa cho chính mình (ownerId phải là userId của bạn)");
        if (horseRepository.existsByHorseNameAndOwner_UserId(request.getHorseName(), request.getOwnerId())) {
            throw new IllegalArgumentException("Bạn đã có ngựa tên '" + request.getHorseName() + "'.");
        }
        User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chủ ngựa với ID: " + request.getOwnerId()));
        if (!ROLE_OWNER.equals(owner.getRoleName())) {
            throw new IllegalArgumentException("Người dùng ID " + request.getOwnerId() + " không phải chủ ngựa.");
        }
        String passport = trimToNull(request.getPassportNo());
        if (passport != null && horseRepository.existsByPassportNoIgnoreCase(passport)) {
            throw new IllegalArgumentException("Số hộ chiếu '" + passport + "' đã được dùng cho ngựa khác.");
        }

        Horse horse = Horse.builder()
                .horseName(request.getHorseName().trim())
                .dob(request.getDob())
                .gender(request.getGender())
                .breed(StringUtils.hasText(request.getBreed()) ? request.getBreed().trim() : "Thoroughbred")
                .pedigree(trimToNull(request.getPedigree()))
                .weightKg(request.getWeightKg())
                .color(trimToNull(request.getColor()))
                .heightHands(request.getHeightHands())
                .passportNo(passport)
                .healthCertIssuer(trimToNull(request.getHealthCertIssuer()))
                .healthCertExpiry(request.getHealthCertExpiry())
                .healthStatus("HEALTHY")
                .isLocked(false)
                .prizeSharePct(java.math.BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .owner(owner)
                .build();
        Horse saved = horseRepository.saveAndFlush(horse);

        // Mỗi lần nộp là một hồ sơ chờ Club Manager duyệt
        registrationRepository.saveAndFlush(HorseRegistration.builder()
                .horse(saved)
                .owner(owner)
                .submittedAt(LocalDateTime.now())
                .status("PENDING_REVIEW")
                .firstIssue(trimToNull(request.getNote()))
                .allowResubmit(false)
                .build());
        // Đọc lại để lấy horse_code và trạng thái hồ sơ do DB tính
        entityManager.refresh(saved);
        return mapToResponseDTO(saved);
    }

    @Override
    @Transactional
    public HorseResponseDTO approveHorse(Integer horseId, HorseApprovalRequestDTO request) {
        Horse horse = findHorse(horseId);
        HorseRegistration reg = pendingRegistration(horse);
        if (horse.getStall() != null) {
            throw new IllegalArgumentException("Ngựa này đã được duyệt và cấp chuồng.");
        }

        // SRS: chỉ duyệt khi mọi mục kiểm tra đều đạt
        if (request.getChecks() != null && !request.getChecks().isEmpty()) {
            Set<String> keys = request.getChecks().stream().map(c -> c.getCheckKey().toUpperCase()).collect(Collectors.toSet());
            if (!keys.containsAll(REQUIRED_CHECKS)) {
                throw new IllegalArgumentException("Checklist phải có đủ 3 mục IDENTITY, HEALTH, OWNERSHIP.");
            }
            if (request.getChecks().stream().anyMatch(c -> !Boolean.TRUE.equals(c.getPassed()))) {
                throw new IllegalArgumentException("Chỉ được duyệt khi mọi mục kiểm tra đều đạt. Hãy từ chối hồ sơ nếu có mục chưa đạt.");
            }
        }

        if (!stallRepository.existsById(request.getStallId()) || stallRepository.existsByIdAndHorseIsNotNull(request.getStallId())) {
            throw new IllegalArgumentException("Chuồng không hợp lệ hoặc đã có ngựa.");
        }
        Stall stall = stallRepository.findById(request.getStallId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuồng với ID: " + request.getStallId()));
        horse.setStall(stall);
        horseRepository.save(horse);

        User manager = currentUser();
        saveChecks(reg, request.getChecks(), manager);
        reg.setStatus("APPROVED");
        reg.setDecisionNote(trimToNull(request.getNote()) != null ? request.getNote().trim() : "Cấp chuồng " + stall.getStallCode());
        reg.setAllowResubmit(false);
        reg.setDecidedBy(manager);
        reg.setDecidedAt(LocalDateTime.now());
        registrationRepository.saveAndFlush(reg);
        entityManager.refresh(horse);
        return mapToResponseDTO(horse);
    }

    @Override
    @Transactional
    public HorseResponseDTO rejectHorse(Integer horseId, HorseRejectRequestDTO request) {
        Horse horse = findHorse(horseId);
        HorseRegistration reg = pendingRegistration(horse);
        if (horse.getStall() != null) {
            throw new IllegalArgumentException("Ngựa đã được cấp chuồng, không thể từ chối hồ sơ.");
        }
        User manager = currentUser();
        saveChecks(reg, request.getChecks(), manager);
        reg.setStatus("REJECTED");
        reg.setDecisionNote(request.getReason().trim());
        reg.setAllowResubmit(Boolean.TRUE.equals(request.getAllowResubmit()));
        reg.setDecidedBy(manager);
        reg.setDecidedAt(LocalDateTime.now());
        registrationRepository.saveAndFlush(reg);
        entityManager.refresh(horse);
        return mapToResponseDTO(horse);
    }

    @Override
    @Transactional
    public HorseResponseDTO resubmitHorse(Integer horseId, HorseResubmitRequestDTO request) {
        Horse horse = findHorse(horseId);
        User owner = currentUser();
        if (!horse.getOwner().getUserId().equals(owner.getUserId())) {
            throw new AccessDeniedException("Bạn chỉ được nộp lại hồ sơ ngựa của chính mình");
        }
        HorseRegistration latest = registrationRepository.findFirstByHorse_HorseIdOrderByRegistrationIdDesc(horseId)
                .orElseThrow(() -> new IllegalArgumentException("Ngựa này chưa có hồ sơ để nộp lại."));
        if (!"REJECTED".equals(latest.getStatus())) {
            throw new IllegalArgumentException("Chỉ nộp lại được hồ sơ đã bị từ chối (hiện tại: " + latest.getStatus() + ").");
        }
        if (!Boolean.TRUE.equals(latest.getAllowResubmit())) {
            throw new IllegalArgumentException("Quản lý CLB không cho phép nộp lại hồ sơ này.");
        }

        if (request.getWeightKg() != null) horse.setWeightKg(request.getWeightKg());
        if (request.getPedigree() != null) horse.setPedigree(trimToNull(request.getPedigree()));
        if (request.getColor() != null) horse.setColor(trimToNull(request.getColor()));
        if (request.getHeightHands() != null) horse.setHeightHands(request.getHeightHands());
        if (request.getHealthCertIssuer() != null) horse.setHealthCertIssuer(trimToNull(request.getHealthCertIssuer()));
        if (request.getHealthCertExpiry() != null) horse.setHealthCertExpiry(request.getHealthCertExpiry());
        if (request.getPassportNo() != null) {
            String passport = trimToNull(request.getPassportNo());
            if (passport != null && horseRepository.existsByPassportNoIgnoreCaseAndHorseIdNot(passport, horseId)) {
                throw new IllegalArgumentException("Số hộ chiếu '" + passport + "' đã được dùng cho ngựa khác.");
            }
            horse.setPassportNo(passport);
        }
        horseRepository.save(horse);

        registrationRepository.saveAndFlush(HorseRegistration.builder()
                .horse(horse)
                .owner(owner)
                .submittedAt(LocalDateTime.now())
                .status("PENDING_REVIEW")
                .firstIssue(trimToNull(request.getNote()))
                .allowResubmit(false)
                .build());
        entityManager.refresh(horse);
        return mapToResponseDTO(horse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistrationHistoryDTO> getRegistrationHistory(Integer horseId) {
        Horse horse = findHorse(horseId);
        checkOwnerAccess(horse.getOwner().getUserId(), "Bạn không phải chủ của ngựa này");
        return registrationRepository.findByHorse_HorseIdOrderByRegistrationIdDesc(horseId).stream()
                .map(r -> RegistrationHistoryDTO.builder()
                        .registrationId(r.getRegistrationId())
                        .registrationCode(r.getRegistrationCode())
                        .horseId(horse.getHorseId())
                        .horseName(horse.getHorseName())
                        .ownerName(r.getOwner().getFullName())
                        .submittedAt(r.getSubmittedAt())
                        .status(r.getStatus())
                        .ownerNote(r.getFirstIssue())
                        .decisionNote(r.getDecisionNote())
                        .allowResubmit(r.getAllowResubmit())
                        .decidedByName(r.getDecidedBy() != null ? r.getDecidedBy().getFullName() : null)
                        .decidedAt(r.getDecidedAt())
                        .checks(r.getChecks().stream()
                                .map(c -> RegistrationCheckDTO.builder()
                                        .checkKey(c.getId().getCheckKey())
                                        .passed(c.getPassed())
                                        .detail(c.getDetail())
                                        .checkedByName(c.getCheckedBy() != null ? c.getCheckedBy().getFullName() : null)
                                        .checkedAt(c.getCheckedAt())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    // Hồ sơ đang chờ duyệt; ngựa tạo trước khi có bảng hồ sơ thì tạo bù một hồ sơ chờ duyệt
    private HorseRegistration pendingRegistration(Horse horse) {
        HorseRegistration latest = registrationRepository.findFirstByHorse_HorseIdOrderByRegistrationIdDesc(horse.getHorseId()).orElse(null);
        if (latest == null && horse.getStall() == null) {
            return registrationRepository.saveAndFlush(HorseRegistration.builder()
                    .horse(horse).owner(horse.getOwner()).submittedAt(horse.getCreatedAt() != null ? horse.getCreatedAt() : LocalDateTime.now())
                    .status("PENDING_REVIEW").allowResubmit(false).build());
        }
        if (latest == null || !"PENDING_REVIEW".equals(latest.getStatus())) {
            throw new IllegalArgumentException("Hồ sơ của ngựa không ở trạng thái chờ duyệt (hiện tại: "
                    + (latest == null ? "đã duyệt" : latest.getStatus()) + ").");
        }
        return latest;
    }

    private void saveChecks(HorseRegistration reg, List<RegistrationCheckDTO> checks, User manager) {
        if (checks == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (RegistrationCheckDTO c : checks) {
            String key = c.getCheckKey().toUpperCase();
            if (!seen.add(key)) {
                throw new IllegalArgumentException("Mục kiểm tra " + key + " bị lặp.");
            }
            RegistrationCheck check = reg.getChecks().stream()
                    .filter(x -> x.getId().getCheckKey().equals(key)).findFirst()
                    .orElseGet(() -> {
                        RegistrationCheck n = RegistrationCheck.builder()
                                .id(new RegistrationCheck.Id(reg.getRegistrationId(), key))
                                .registration(reg).build();
                        reg.getChecks().add(n);
                        return n;
                    });
            check.setPassed(c.getPassed());
            check.setDetail(trimToNull(c.getDetail()));
            check.setCheckedBy(manager);
            check.setCheckedAt(LocalDateTime.now());
        }
    }

    // ==========================================
    // CẬP NHẬT NGỰA
    // ==========================================

    @Override
    @Transactional
    public HorseResponseDTO updateHorse(Integer horseId, HorseUpdateRequestDTO request) {
        Horse horse = findHorse(horseId);
        if (request.getWeightKg() != null) {
            horse.setWeightKg(request.getWeightKg());
        }
        if (request.getHealthStatus() != null && !request.getHealthStatus().equalsIgnoreCase(horse.getHealthStatus())) {
            // Đánh giá sức khỏe là chuyên môn y tế, chỉ Bác sĩ thú y được thay đổi
            if (!currentUserHasRole(ROLE_VET)) {
                throw new AccessDeniedException("Chỉ Bác sĩ thú y mới được thay đổi trạng thái sức khỏe (healthStatus)");
            }
            horse.setHealthStatus(request.getHealthStatus().toUpperCase());
        }
        if (request.getIsLocked() != null && !request.getIsLocked().equals(horse.getIsLocked())) {
            // Khóa/mở khóa huấn luyện là quyết định y tế, chỉ Bác sĩ thú y được thay đổi
            if (!currentUserHasRole(ROLE_VET)) {
                throw new AccessDeniedException("Chỉ Bác sĩ thú y mới được thay đổi trạng thái khóa huấn luyện (isLocked)");
            }
            horse.setIsLocked(request.getIsLocked());
        }
        return mapToResponseDTO(horseRepository.save(horse));
    }

    @Override
    @Transactional
    public void lockHorseTraining(Integer horseId, String reason) {
        Horse horse = findHorse(horseId);
        horse.setIsLocked(true);
        horseRepository.save(horse);
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
