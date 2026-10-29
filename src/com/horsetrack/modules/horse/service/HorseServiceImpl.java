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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class HorseServiceImpl implements HorseService {

    private static final String ROLE_OWNER = "Horse Owner";
    private static final String ROLE_VET = "Veterinarian";
    // 3 mục kiểm tra bắt buộc của hồ sơ (Registration_Checks.check_key)
    // Không còn kiểm tra giấy chứng nhận sức khỏe (đã bỏ khỏi hồ sơ đăng ký)
    private static final Set<String> REQUIRED_CHECKS = Set.of("IDENTITY", "OWNERSHIP");
    private static final BigDecimal CM_PER_HAND = new BigDecimal("10.16");

    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final StallRepository stallRepository;
    private final HorseRegistrationRepository registrationRepository;
    private final EntityManager entityManager;
    private final com.horsetrack.equinecare.modules.audit.service.AuditTrail auditTrail;
    private final com.horsetrack.equinecare.modules.training.service.TrainingLockHandler trainingLockHandler;
    private final HorseAccessGuard horseAccessGuard;
    private final com.horsetrack.equinecare.modules.training.repository.TrainingPlanRepository trainingPlanRepository;
    private final com.horsetrack.equinecare.modules.notification.service.EmailService emailService;
    private final com.horsetrack.equinecare.modules.assignment.service.AssignmentRequestWriter assignmentRequests;
    private final com.horsetrack.equinecare.modules.medical.service.HorseHealthService injuryService;
    private final RegistrationAttachmentService attachmentService;
    private final com.horsetrack.equinecare.modules.medical.service.HorseConditionNotifier conditionNotifier;

    public HorseServiceImpl(HorseRepository horseRepository, UserRepository userRepository, StallRepository stallRepository,
                            HorseRegistrationRepository registrationRepository, EntityManager entityManager,
                            com.horsetrack.equinecare.modules.audit.service.AuditTrail auditTrail,
                            com.horsetrack.equinecare.modules.training.service.TrainingLockHandler trainingLockHandler,
                            HorseAccessGuard horseAccessGuard,
                            com.horsetrack.equinecare.modules.training.repository.TrainingPlanRepository trainingPlanRepository,
                            com.horsetrack.equinecare.modules.notification.service.EmailService emailService,
                            com.horsetrack.equinecare.modules.assignment.service.AssignmentRequestWriter assignmentRequests,
                            com.horsetrack.equinecare.modules.medical.service.HorseHealthService injuryService,
                            RegistrationAttachmentService attachmentService,
                            com.horsetrack.equinecare.modules.medical.service.HorseConditionNotifier conditionNotifier) {
        this.horseRepository = horseRepository;
        this.userRepository = userRepository;
        this.stallRepository = stallRepository;
        this.registrationRepository = registrationRepository;
        this.entityManager = entityManager;
        this.auditTrail = auditTrail;
        this.trainingLockHandler = trainingLockHandler;
        this.horseAccessGuard = horseAccessGuard;
        this.trainingPlanRepository = trainingPlanRepository;
        this.emailService = emailService;
        this.assignmentRequests = assignmentRequests;
        this.injuryService = injuryService;
        this.attachmentService = attachmentService;
        this.conditionNotifier = conditionNotifier;
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
                .ownerName(horse.getOwner().getFullName())
                .ownerPhone(horse.getOwner().getPhone())
                .stallId(horse.getStall() != null ? horse.getStall().getStallId() : null)
                .stallCode(horse.getStall() != null ? horse.getStall().getStallCode() : null)
                .approvalStatus(horse.getApprovalStatus())
                .createdAt(horse.getCreatedAt())
                .horseCode(horse.getHorseCode())
                .color(horse.getColor())
                .heightCm(horse.getHeightCm() != null ? horse.getHeightCm()
                        : horse.getHeightHands() != null ? horse.getHeightHands().multiply(CM_PER_HAND).setScale(1, java.math.RoundingMode.HALF_UP) : null)
                .registrationId(latest != null ? latest.getRegistrationId() : null)
                .registrationCode(latest != null ? latest.getRegistrationCode() : null)
                .rejectReason(rejected ? latest.getDecisionNote() : null)
                .allowResubmit(rejected ? latest.getAllowResubmit() : null)
                .assignedTrainerId(horse.getAssignedTrainer() != null ? horse.getAssignedTrainer().getUserId() : null)
                .assignedTrainerName(horse.getAssignedTrainer() != null ? horse.getAssignedTrainer().getFullName() : null)
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
        Integer trainerId = horseAccessGuard.trainerScopeId();
        if (trainerId != null) {
            // Trainer chỉ thấy ngựa được phân công (đều là ngựa đã duyệt), không thấy hồ sơ chờ duyệt / bị từ chối
            if ("PENDING".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
                return Page.empty(pageable);
            }
            return horseRepository.findByAssignedTrainer_UserId(trainerId, pageable).map(this::mapToResponseDTO);
        }
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
        horseAccessGuard.checkAssigned(horse);
        HorseResponseDTO dto = mapToResponseDTO(horse);
        dto.setInjuries(injuryService.listForHorse(horse.getHorseId()));
        return dto;
    }

    @Override
    @Transactional
    public HorseResponseDTO unassignTrainer(Integer horseId) {
        Horse horse = findHorse(horseId);
        User previous = horse.getAssignedTrainer();
        if (previous == null) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " chưa được phân công HLV.");
        }
        assertNoOpenPlan(horse);
        assignmentRequests.openTrainerRelease(horse, previous, currentUser());
        return mapToResponseDTO(horse);
    }

    @Override
    @Transactional
    public HorseResponseDTO applyTrainerRelease(Integer horseId) {
        Horse horse = findHorse(horseId);
        User previous = horse.getAssignedTrainer();
        if (previous == null) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " chưa được phân công HLV.");
        }
        assertNoOpenPlan(horse);
        horse.setAssignedTrainer(null);
        horseRepository.save(horse);
        auditTrail.record("UNASSIGN_TRAINER", "Horses", "assigned_trainer_id=" + previous.getUserId(),
                "assigned_trainer_id=null | ngựa " + horse.getHorseName() + " (horse_id=" + horse.getHorseId() + ")");
        mailTrainer(previous, "[HorseTrack] Bạn không còn phụ trách ngựa " + horse.getHorseName(),
                "<p>Xin chào <b>" + esc(previous.getFullName()) + "</b>,</p>"
                        + "<p>Bạn đã xác nhận bỏ phân công ngựa <b>" + esc(horse.getHorseName()) + "</b>.</p>");
        return mapToResponseDTO(horse);
    }

    @Override
    @Transactional
    public HorseResponseDTO assignTrainer(Integer horseId, HorseAssignTrainerRequestDTO request) {
        PreparedTrainer prepared = prepareTrainer(horseId, request.getTrainerId());
        assignmentRequests.openTrainerHorse(prepared.horse, prepared.trainer, prepared.previous, currentUser());
        return mapToResponseDTO(prepared.horse);
    }

    @Override
    @Transactional
    public HorseResponseDTO applyTrainerAssignment(Integer horseId, Integer trainerId) {
        PreparedTrainer prepared = prepareTrainer(horseId, trainerId);
        Horse horse = prepared.horse;
        User trainer = prepared.trainer;
        User previous = prepared.previous;
        horse.setAssignedTrainer(trainer);
        horseRepository.save(horse);

        int handedOver = 0;
        for (String st : com.horsetrack.equinecare.modules.training.service.TrainingPlanActivator.OPEN_PLAN_STATUSES) {
            for (var plan : trainingPlanRepository.findByHorse_HorseIdAndStatus(horse.getHorseId(), st)) {
                plan.setHeadTrainer(trainer);
                trainingPlanRepository.save(plan);
                handedOver++;
            }
        }

        auditTrail.record("ASSIGN_TRAINER", "Horses",
                previous != null ? "assigned_trainer_id=" + previous.getUserId() : "assigned_trainer_id=null",
                "assigned_trainer_id=" + trainer.getUserId() + " | ngựa " + horse.getHorseName() + " (horse_id=" + horse.getHorseId()
                        + ") | bàn giao " + handedOver + " giáo án");

        String stall = horse.getStall() != null ? " (chuồng " + esc(horse.getStall().getStallCode()) + ")" : "";
        mailTrainer(trainer, "[HorseTrack] Bạn đã nhận ngựa " + horse.getHorseName(),
                "<p>Xin chào <b>" + esc(trainer.getFullName()) + "</b>,</p>"
                        + "<p>Bạn đã xác nhận phụ trách ngựa <b>" + esc(horse.getHorseName()) + "</b>" + stall + ". Phân công có hiệu lực.</p>"
                        + (handedOver > 0 ? "<p>Bạn tiếp nhận " + handedOver + " giáo án đang chạy của ngựa này.</p>" : ""));
        if (previous != null) {
            mailTrainer(previous, "[HorseTrack] Ngựa " + horse.getHorseName() + " đã chuyển sang HLV khác",
                    "<p>Xin chào <b>" + esc(previous.getFullName()) + "</b>,</p>"
                            + "<p>HLV <b>" + esc(trainer.getFullName()) + "</b> đã xác nhận nhận ngựa <b>" + esc(horse.getHorseName()) + "</b>. Bạn không còn phụ trách ngựa này.</p>");
        }
        return mapToResponseDTO(horse);
    }

    private void assertNoOpenPlan(Horse horse) {
        for (String st : com.horsetrack.equinecare.modules.training.service.TrainingPlanActivator.OPEN_PLAN_STATUSES) {
            if (!trainingPlanRepository.findByHorse_HorseIdAndStatus(horse.getHorseId(), st).isEmpty()) {
                throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " đang có giáo án " + st
                        + ". Hãy phân công HLV khác để bàn giao giáo án, hoặc kết thúc / hủy giáo án trước khi bỏ phân công.");
            }
        }
    }

    private PreparedTrainer prepareTrainer(Integer horseId, Integer trainerId) {
        Horse horse = findHorse(horseId);
        if (horse.getStall() == null) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " chưa được duyệt và cấp chuồng, chưa thể phân công HLV.");
        }
        User trainer = userRepository.findById(trainerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên với ID: " + trainerId));
        if (!HorseAccessGuard.ROLE_TRAINER.equals(trainer.getRoleName())) {
            throw new IllegalArgumentException("Chỉ được phân công nhân viên có vai trò Trainer (người được chọn có vai trò " + trainer.getRoleName() + ").");
        }
        if (Boolean.FALSE.equals(trainer.getIsActive())) {
            throw new IllegalArgumentException("Tài khoản Trainer " + trainer.getFullName() + " đã bị vô hiệu hóa.");
        }
        com.horsetrack.equinecare.modules.auth.service.StaffAccountService.requireConfirmed(trainer, "phân công phụ trách ngựa");
        User previous = horse.getAssignedTrainer();
        if (previous != null && previous.getUserId().equals(trainer.getUserId())) {
            throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " đã được phân công cho " + trainer.getFullName() + ".");
        }
        return new PreparedTrainer(horse, trainer, previous);
    }

    private void mailTrainer(User user, String subject, String html) {
        if (user == null || !StringUtils.hasText(user.getEmail())) return;
        try {
            emailService.sendAsync("TRAINER_ASSIGNMENT", user.getEmail(), subject, html);
        } catch (Exception ignored) {
            // Email chỉ là thông báo
        }
    }

    private static String esc(String value) {
        return org.springframework.web.util.HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private record PreparedTrainer(Horse horse, User trainer, User previous) {
    }

    // ==========================================
    // HỒ SƠ ĐĂNG KÝ: NỘP, DUYỆT, TỪ CHỐI, NỘP LẠI
    // ==========================================

    @Override
    @Transactional
    public HorseResponseDTO registerHorse(HorseRegistrationRequestDTO request) {
        // Chủ ngựa đăng ký cho chính mình (không cần gửi ownerId); Club Manager đăng ký hộ thì phải chọn chủ ngựa
        Integer ownerId = request.getOwnerId();
        if (currentUserHasRole(ROLE_OWNER)) {
            Integer me = currentUser().getUserId();
            if (ownerId != null && !ownerId.equals(me)) {
                throw new AccessDeniedException("Chủ ngựa chỉ được đăng ký ngựa cho chính mình (ownerId phải là userId của bạn)");
            }
            ownerId = me;
        } else if (ownerId == null) {
            throw new IllegalArgumentException("Phải chọn chủ ngựa (ownerId).");
        }
        if (horseRepository.existsByHorseNameAndOwner_UserId(request.getHorseName(), ownerId)) {
            throw new IllegalArgumentException("Bạn đã có ngựa tên '" + request.getHorseName() + "'.");
        }
        Integer finalOwnerId = ownerId;
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chủ ngựa với ID: " + finalOwnerId));
        if (!ROLE_OWNER.equals(owner.getRoleName())) {
            throw new IllegalArgumentException("Người dùng ID " + ownerId + " không phải chủ ngựa.");
        }
        // Số điện thoại liên hệ lấy từ hồ sơ chủ ngựa, không nhập lại trên hồ sơ ngựa
        if (!StringUtils.hasText(owner.getPhone())) {
            throw new IllegalArgumentException("Tài khoản chủ ngựa chưa có số điện thoại. Cập nhật tại Hồ sơ cá nhân (PUT /api/v1/account/my-profile) trước khi đăng ký ngựa.");
        }
        BigDecimal heightCm = heightCm(request.getHeightCm(), request.getHeightHands());

        Horse horse = Horse.builder()
                .horseName(request.getHorseName().trim())
                .dob(request.getDob())
                .gender(request.getGender())
                .breed(StringUtils.hasText(request.getBreed()) ? request.getBreed().trim() : "Thoroughbred")
                .pedigree(trimToNull(request.getPedigree()))
                .weightKg(request.getWeightKg())
                .color(trimToNull(request.getColor()))
                .heightCm(heightCm)
                .heightHands(toHands(heightCm))
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

        // Quản lý CLB chỉ duyệt khi hồ sơ có ảnh/tệp giấy tờ sở hữu thật để đối chiếu nội dung
        List<com.horsetrack.equinecare.modules.horse.dto.RegistrationAttachmentDTO> docs =
                attachmentService.byRegistration(List.of(reg.getRegistrationId())).getOrDefault(reg.getRegistrationId(), List.of());
        if (docs.stream().noneMatch(d -> "OWNERSHIP".equalsIgnoreCase(d.getDocType()))) {
            throw new IllegalArgumentException("Hồ sơ chưa có ảnh/tệp giấy tờ sở hữu. Không thể duyệt khi chưa xem được nội dung giấy tờ; hãy từ chối và cho phép nộp lại.");
        }

        // SRS: chỉ duyệt khi mọi mục kiểm tra đều đạt
        if (request.getChecks() != null && !request.getChecks().isEmpty()) {
            Set<String> keys = request.getChecks().stream().map(c -> c.getCheckKey().toUpperCase()).collect(Collectors.toSet());
            if (!keys.containsAll(REQUIRED_CHECKS)) {
                throw new IllegalArgumentException("Checklist phải có đủ 2 mục IDENTITY, OWNERSHIP.");
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
        // F1-05 (BR_18/BR28): chỉ xếp ngựa vào chuồng đã vệ sinh sạch
        if (!"CLEAN".equalsIgnoreCase(stall.getCleanStatus())) {
            throw new IllegalArgumentException("Chuồng " + stall.getStallCode() + " đang ở trạng thái " + stall.getCleanStatus()
                    + ". Chỉ xếp ngựa vào chuồng CLEAN.");
        }
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
        auditTrail.record("APPROVE_HORSE", "Horse_Registrations", "PENDING_REVIEW",
                "APPROVED " + reg.getRegistrationCode() + " | ngựa " + horse.getHorseName() + " (horse_id=" + horse.getHorseId() + ") | chuồng " + stall.getStallCode());
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
        auditTrail.record("REJECT_HORSE", "Horse_Registrations", "PENDING_REVIEW",
                "REJECTED " + reg.getRegistrationCode() + " | ngựa " + horse.getHorseName() + " (horse_id=" + horse.getHorseId() + ") | cho nộp lại: "
                        + reg.getAllowResubmit() + " | Lý do: " + reg.getDecisionNote());
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
        BigDecimal heightCm = heightCm(request.getHeightCm(), request.getHeightHands());
        if (heightCm != null) {
            horse.setHeightCm(heightCm);
            horse.setHeightHands(toHands(heightCm));
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
        List<HorseRegistration> regs = registrationRepository.findByHorse_HorseIdOrderByRegistrationIdDesc(horseId);
        Map<Integer, List<com.horsetrack.equinecare.modules.horse.dto.RegistrationAttachmentDTO>> attachments =
                attachmentService.byRegistration(regs.stream().map(HorseRegistration::getRegistrationId).toList());
        return regs.stream()
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
                        .attachments(attachments.getOrDefault(r.getRegistrationId(), List.of()))
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
        horseAccessGuard.checkAssigned(horse);
        if (request.getWeightKg() != null) {
            horse.setWeightKg(request.getWeightKg());
        }
        if (request.getHealthStatus() != null && !request.getHealthStatus().equalsIgnoreCase(horse.getHealthStatus())) {
            // Đánh giá sức khỏe là chuyên môn y tế, chỉ Bác sĩ thú y được thay đổi
            if (!currentUserHasRole(ROLE_VET)) {
                throw new AccessDeniedException("Chỉ Bác sĩ thú y mới được thay đổi trạng thái sức khỏe (healthStatus)");
            }
            String nextStatus = request.getHealthStatus().toUpperCase();
            horse.setHealthStatus(nextStatus);
            horseRepository.save(horse);
            conditionNotifier.onStatus(horse, nextStatus, "Bác sĩ thú y cập nhật trạng thái sức khỏe.");
        }
        if (request.getIsLocked() != null && !request.getIsLocked().equals(horse.getIsLocked())) {
            // Khóa/mở khóa huấn luyện là quyết định y tế, chỉ Bác sĩ thú y được thay đổi
            if (!currentUserHasRole(ROLE_VET)) {
                throw new AccessDeniedException("Chỉ Bác sĩ thú y mới được thay đổi trạng thái khóa huấn luyện (isLocked)");
            }
            horse.setIsLocked(request.getIsLocked());
            horseRepository.save(horse);
            if (Boolean.TRUE.equals(request.getIsLocked())) {
                int cancelled = trainingLockHandler.onHorseLocked(horse, "Bác sĩ thú y khóa huấn luyện");
                auditTrail.record("VET_LOCK", "Horses", "isLocked=false", "isLocked=true | ngựa " + horse.getHorseName()
                        + " (horse_id=" + horse.getHorseId() + ") | hủy " + cancelled + " buổi tập");
            } else {
                trainingLockHandler.onHorseUnlocked(horse);
                auditTrail.record("VET_UNLOCK", "Horses", "isLocked=true", "isLocked=false | ngựa " + horse.getHorseName() + " (horse_id=" + horse.getHorseId() + ")");
            }
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

    
    private static BigDecimal heightCm(BigDecimal cm, BigDecimal hands) {
        if (cm != null) {
            return cm.setScale(1, java.math.RoundingMode.HALF_UP);
        }
        if (hands == null) {
            return null;
        }
        BigDecimal converted = hands.multiply(CM_PER_HAND).setScale(1, java.math.RoundingMode.HALF_UP);
        if (converted.compareTo(new BigDecimal("120")) < 0 || converted.compareTo(new BigDecimal("205")) > 0) {
            throw new IllegalArgumentException("Chiều cao phải từ 120 đến 205 cm (gửi heightCm theo cm).");
        }
        return converted;
    }

    private static BigDecimal toHands(BigDecimal cm) {
        return cm == null ? null : cm.divide(CM_PER_HAND, 1, java.math.RoundingMode.HALF_UP);
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
