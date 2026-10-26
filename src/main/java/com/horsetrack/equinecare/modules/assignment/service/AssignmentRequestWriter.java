package com.horsetrack.equinecare.modules.assignment.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.assignment.dto.AssignmentRequestResponseDTO;
import com.horsetrack.equinecare.modules.assignment.entity.AssignmentRequest;
import com.horsetrack.equinecare.modules.assignment.repository.AssignmentRequestRepository;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tạo và đọc yêu cầu phân công. Chưa ghi assigned_trainer / assigned_barn_zone. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentRequestWriter {

    public static final String TRAINER_HORSE = "TRAINER_HORSE";
    public static final String TRAINER_RELEASE = "TRAINER_RELEASE";
    public static final String GROOM_ZONE = "GROOM_ZONE";
    public static final String PENDING = "PENDING";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AssignmentRequestRepository repository;
    private final UserRepository userRepository;
    private final HorseRepository horseRepository;
    private final EmailService emailService;
    private final AuditTrail auditTrail;

    @Transactional
    public AssignmentRequest openTrainerHorse(Horse horse, User trainer, User previous, User manager) {
        cancelOpenForHorse(horse.getHorseId());
        AssignmentRequest saved = save(TRAINER_HORSE, trainer, previous, horse.getHorseId(), null, null, manager);
        String stall = horse.getStall() != null ? horse.getStall().getStallCode() : null;
        mail(trainer, "TRAINER_ASSIGNMENT", "[HorseTrack] Chờ bạn xác nhận phụ trách ngựa " + horse.getHorseName(),
                "<p>Xin chào <b>" + esc(trainer.getFullName()) + "</b>,</p>"
                        + "<p>Quản lý CLB đề xuất phân công bạn phụ trách ngựa <b>" + esc(horse.getHorseName()) + "</b>"
                        + (stall != null ? " (chuồng " + esc(stall) + ")" : "")
                        + (previous != null ? ", hiện do HLV <b>" + esc(previous.getFullName()) + "</b> phụ trách" : "")
                        + ".</p><p>Phân công <b>chưa có hiệu lực</b>. Hãy đăng nhập HorseTrack, mở trang tổng quan và bấm Xác nhận hoặc Từ chối (kèm lý do).</p>");
        if (previous != null && !previous.getUserId().equals(trainer.getUserId())) {
            mail(previous, "TRAINER_ASSIGNMENT", "[HorseTrack] Đề xuất chuyển ngựa " + horse.getHorseName(),
                    "<p>Xin chào <b>" + esc(previous.getFullName()) + "</b>,</p>"
                            + "<p>Quản lý CLB đề xuất chuyển ngựa <b>" + esc(horse.getHorseName()) + "</b> sang HLV <b>"
                            + esc(trainer.getFullName()) + "</b>.</p>"
                            + "<p>Bạn <b>vẫn phụ trách</b> ngựa này cho đến khi HLV mới xác nhận. Đây là thông báo, không cần bạn chấp thuận.</p>");
        }
        return saved;
    }

    @Transactional
    public AssignmentRequest openTrainerRelease(Horse horse, User trainer, User manager) {
        cancelOpenForHorse(horse.getHorseId());
        AssignmentRequest saved = save(TRAINER_RELEASE, trainer, trainer, horse.getHorseId(), null, null, manager);
        mail(trainer, "TRAINER_ASSIGNMENT", "[HorseTrack] Chờ bạn xác nhận bỏ ngựa " + horse.getHorseName(),
                "<p>Xin chào <b>" + esc(trainer.getFullName()) + "</b>,</p>"
                        + "<p>Quản lý CLB muốn bỏ phân công bạn khỏi ngựa <b>" + esc(horse.getHorseName()) + "</b>.</p>"
                        + "<p>Bạn <b>vẫn phụ trách</b> cho đến khi xác nhận. Hãy đăng nhập và bấm Xác nhận hoặc Từ chối (kèm lý do).</p>");
        return saved;
    }

    @Transactional
    public AssignmentRequest openGroomZone(User groom, String oldZone, String newZone, User manager) {
        repository.findByStatusAndTargetUserIdAndRequestType(PENDING, groom.getUserId(), GROOM_ZONE)
                .forEach(this::cancel);
        AssignmentRequest saved = save(GROOM_ZONE, groom, null, null, newZone, oldZone, manager);
        String change = newZone == null
                ? "bỏ phân công khu" + (oldZone != null ? " <b>" + esc(oldZone) + "</b>" : "")
                : "phân công bạn phụ trách <b>" + esc(newZone) + "</b>" + (oldZone != null ? " (hiện tại: " + esc(oldZone) + ")" : "");
        mail(groom, "GROOM_ZONE", "[HorseTrack] Chờ bạn xác nhận khu chăm sóc",
                "<p>Xin chào <b>" + esc(groom.getFullName()) + "</b>,</p>"
                        + "<p>Quản lý CLB muốn " + change + ".</p>"
                        + "<p>Khu <b>chưa đổi</b> cho đến khi bạn xác nhận. Hãy đăng nhập HorseTrack và bấm Xác nhận hoặc Từ chối (kèm lý do).</p>");
        return saved;
    }

    public Integer currentUserId() {
        return currentUser().getUserId();
    }

    @Transactional(readOnly = true)
    public List<AssignmentRequestResponseDTO> pendingFor(Integer userId) {
        return toDtos(repository.findByStatusAndTargetUserIdOrderByCreatedAtDesc(PENDING, userId));
    }

    @Transactional(readOnly = true)
    public List<AssignmentRequestResponseDTO> allPending() {
        return toDtos(repository.findByStatusOrderByCreatedAtDesc(PENDING));
    }

    @Transactional
    public AssignmentRequest requireMine(Integer requestId) {
        AssignmentRequest req = repository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu phân công #" + requestId));
        if (!PENDING.equals(req.getStatus())) {
            throw new IllegalArgumentException("Yêu cầu này đã được xử lý.");
        }
        User me = currentUser();
        if (!req.getTargetUserId().equals(me.getUserId())) {
            throw new AccessDeniedException("Chỉ người được phân công mới xác nhận hoặc từ chối yêu cầu này.");
        }
        return req;
    }

    @Transactional
    public void mark(AssignmentRequest req, String status, String reason) {
        req.setStatus(status);
        req.setRejectReason(reason);
        req.setDecidedAt(LocalDateTime.now());
        repository.save(req);
    }

    public void mailRejected(AssignmentRequest req, String reason) {
        User target = userRepository.findById(req.getTargetUserId()).orElse(null);
        User manager = userRepository.findById(req.getCreatedBy()).orElse(null);
        User previous = req.getPreviousUserId() != null ? userRepository.findById(req.getPreviousUserId()).orElse(null) : null;
        String what = describe(req);
        String who = target != null ? target.getFullName() : "Nhân viên";
        if (manager != null) {
            mail(manager, "ASSIGNMENT_REJECTED", "[HorseTrack] Từ chối phân công",
                    "<p>Xin chào <b>" + esc(manager.getFullName()) + "</b>,</p>"
                            + "<p><b>" + esc(who) + "</b> đã từ chối: " + what + ".</p>"
                            + "<p>Lý do: " + esc(reason) + "</p>");
        }
        if (previous != null && (target == null || !previous.getUserId().equals(target.getUserId())) && TRAINER_HORSE.equals(req.getRequestType())) {
            mail(previous, "ASSIGNMENT_REJECTED", "[HorseTrack] Phân công ngựa không đổi",
                    "<p>Xin chào <b>" + esc(previous.getFullName()) + "</b>,</p>"
                            + "<p>HLV được đề xuất đã từ chối. " + what + " Bạn vẫn phụ trách ngựa này.</p>"
                            + "<p>Lý do: " + esc(reason) + "</p>");
        }
    }

    private AssignmentRequest save(String type, User target, User previous, Integer horseId, String zone, String previousZone, User manager) {
        AssignmentRequest saved = repository.save(AssignmentRequest.builder()
                .requestType(type)
                .status(PENDING)
                .targetUserId(target.getUserId())
                .horseId(horseId)
                .previousUserId(previous != null ? previous.getUserId() : null)
                .barnZone(zone)
                .previousZone(previousZone)
                .createdBy(manager.getUserId())
                .createdAt(LocalDateTime.now())
                .build());
        auditTrail.record("REQUEST_ASSIGNMENT", "Assignment_Requests", "status=none",
                "status=PENDING | " + type + " | request_id=" + saved.getRequestId() + " | target_user_id=" + target.getUserId()
                        + (horseId != null ? " | horse_id=" + horseId : "")
                        + (zone != null ? " | barn_zone=" + zone : ""));
        return saved;
    }

    private void cancelOpenForHorse(Integer horseId) {
        repository.findByStatusAndHorseId(PENDING, horseId).forEach(this::cancel);
    }

    private void cancel(AssignmentRequest req) {
        req.setStatus("CANCELLED");
        req.setDecidedAt(LocalDateTime.now());
        repository.save(req);
    }

    private List<AssignmentRequestResponseDTO> toDtos(List<AssignmentRequest> rows) {
        Map<Integer, User> users = new HashMap<>();
        Map<Integer, String> horses = new HashMap<>();
        return rows.stream().map(r -> {
            User target = users.computeIfAbsent(r.getTargetUserId(), this::user);
            User previous = r.getPreviousUserId() == null ? null : users.computeIfAbsent(r.getPreviousUserId(), this::user);
            String horseName = r.getHorseId() == null ? null : horses.computeIfAbsent(r.getHorseId(), id ->
                    horseRepository.findById(id).map(Horse::getHorseName).orElse(null));
            return AssignmentRequestResponseDTO.builder()
                    .requestId(r.getRequestId())
                    .requestType(r.getRequestType())
                    .status(r.getStatus())
                    .targetUserId(r.getTargetUserId())
                    .targetName(target != null ? target.getFullName() : null)
                    .horseId(r.getHorseId())
                    .horseName(horseName)
                    .previousUserId(r.getPreviousUserId())
                    .previousName(previous != null ? previous.getFullName() : null)
                    .barnZone(r.getBarnZone())
                    .previousZone(r.getPreviousZone())
                    .createdAt(r.getCreatedAt() == null ? null : WHEN.format(r.getCreatedAt()))
                    .build();
        }).toList();
    }

    private User user(Integer id) {
        return userRepository.findById(id).orElse(null);
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }

    private String describe(AssignmentRequest req) {
        String horse = req.getHorseId() == null ? "" : horseRepository.findById(req.getHorseId()).map(Horse::getHorseName).orElse("#" + req.getHorseId());
        return switch (req.getRequestType()) {
            case TRAINER_HORSE -> "nhận ngựa " + horse;
            case TRAINER_RELEASE -> "bỏ phân công ngựa " + horse;
            case GROOM_ZONE -> req.getBarnZone() == null ? "bỏ khu chăm sóc" : "nhận " + req.getBarnZone();
            default -> req.getRequestType();
        };
    }

    private void mail(User user, String purpose, String subject, String html) {
        if (user == null || !StringUtils.hasText(user.getEmail())) return;
        try {
            emailService.sendAsync(purpose, user.getEmail(), subject, html);
        } catch (Exception ex) {
            log.warn("Không gửi được email {} tới {}: {}", purpose, user.getEmail(), ex.getMessage());
        }
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
