package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.dto.GroomZoneResponseDTO;
import com.horsetrack.equinecare.modules.auth.entity.GroomProfile;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.GroomProfileRepository;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.care.repository.CareTaskRepository;
import com.horsetrack.equinecare.modules.horse.repository.StallRepository;
import com.horsetrack.equinecare.modules.assignment.service.AssignmentRequestWriter;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import com.horsetrack.equinecare.modules.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Club Manager phân công / đổi / bỏ khu chăm sóc của Groom (Groom_Profiles.assigned_barn_zone).
 * Groom chỉ nhận buổi tập và việc chăm sóc của ngựa trong khu mình (F21-04, F4-03), nên không cho đổi hoặc bỏ khu
 * khi Groom còn việc chưa xong ở khu hiện tại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffAssignmentService {

    private static final List<String> OPEN_TASKS = List.of("TODO", "IN_PROGRESS");
    private static final List<String> OPEN_SESSIONS = com.horsetrack.equinecare.modules.training.service.TrainingPlanActivator.ACTIVE_SESSION_STATUSES;

    private final UserRepository userRepository;
    private final GroomProfileRepository groomProfileRepository;
    private final StallRepository stallRepository;
    private final CareTaskRepository careTaskRepository;
    private final TrainingSessionRepository sessionRepository;
    private final AuditTrail auditTrail;
    private final EmailService emailService;
    private final AssignmentRequestWriter assignmentRequests;

    @Transactional(readOnly = true)
    public GroomZoneResponseDTO getGroomZone(Integer userId) {
        User groom = findGroom(userId);
        GroomProfile profile = groomProfileRepository.findById(userId).orElse(null);
        return toDTO(groom, profile != null ? profile.getAssignedBarnZone() : null);
    }

    /** Gửi yêu cầu. Khu chỉ đổi khi người chăm xác nhận. */
    @Transactional
    public GroomZoneResponseDTO updateGroomZone(Integer userId, String barnZone) {
        ZoneChange change = prepareZone(userId, barnZone);
        assignmentRequests.openGroomZone(change.groom, change.oldZone, change.newZone, currentUser());
        return toDTO(change.groom, change.oldZone);
    }

    /** Ghi khu sau khi người chăm xác nhận. */
    @Transactional
    public GroomZoneResponseDTO applyGroomZone(Integer userId, String barnZone) {
        ZoneChange change = prepareZone(userId, barnZone);
        User groom = change.groom;
        String newZone = change.newZone;
        String oldZone = change.oldZone;
        GroomProfile profile = groomProfileRepository.findById(userId)
                .orElseGet(() -> GroomProfile.builder().userId(userId).user(groom).build());
        profile.setAssignedBarnZone(newZone);
        groomProfileRepository.save(profile);
        auditTrail.record(newZone == null ? "UNASSIGN_GROOM_ZONE" : "ASSIGN_GROOM_ZONE", "Groom_Profiles",
                "assigned_barn_zone=" + oldZone, "assigned_barn_zone=" + newZone + " | Groom " + groom.getFullName() + " (user_id=" + userId + ")");
        if (StringUtils.hasText(groom.getEmail())) {
            try {
                emailService.sendAsync("GROOM_ZONE", groom.getEmail(), "[HorseTrack] Khu chăm sóc đã có hiệu lực",
                        "<p>Xin chào <b>" + HtmlUtils.htmlEscape(groom.getFullName()) + "</b>,</p>"
                                + (newZone == null
                                ? "<p>Bạn đã xác nhận bỏ khu chăm sóc" + (oldZone != null ? " (" + HtmlUtils.htmlEscape(oldZone) + ")" : "") + ".</p>"
                                : "<p>Bạn đã xác nhận phụ trách <b>" + HtmlUtils.htmlEscape(newZone) + "</b>"
                                        + (oldZone != null ? " (trước đây: " + HtmlUtils.htmlEscape(oldZone) + ")" : "") + ".</p>"));
            } catch (Exception ex) {
                log.warn("Không gửi được email đổi khu cho {}: {}", groom.getEmail(), ex.getMessage());
            }
        }
        return toDTO(groom, newZone);
    }

    private ZoneChange prepareZone(Integer userId, String barnZone) {
        User groom = findGroom(userId);
        String newZone = StringUtils.hasText(barnZone) ? barnZone.trim() : null;
        if (newZone != null) {
            StaffAccountService.requireConfirmed(groom, "phân công khu chuồng");
            String finalZone = newZone;
            newZone = stallRepository.findDistinctBarnZones().stream()
                    .filter(z -> z != null && z.trim().equalsIgnoreCase(finalZone))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Khu '" + finalZone + "' không tồn tại. Các khu hiện có: "
                            + String.join(", ", stallRepository.findDistinctBarnZones()) + "."));
        }
        GroomProfile profile = groomProfileRepository.findById(userId).orElse(null);
        String oldZone = profile != null ? profile.getAssignedBarnZone() : null;
        if (Objects.equals(normalize(oldZone), normalize(newZone))) {
            throw new IllegalArgumentException(newZone == null
                    ? "Groom " + groom.getFullName() + " hiện chưa được phân công khu nào."
                    : "Groom " + groom.getFullName() + " đang phụ trách " + newZone + ".");
        }
        if (oldZone != null) {
            long tasks = careTaskRepository.countByGroom_UserIdAndStatusInAndTaskDateGreaterThanEqual(userId, OPEN_TASKS, LocalDate.now());
            long sessions = sessionRepository.countByAssignedTo_UserIdAndStatusInAndSessionDateAfter(userId, OPEN_SESSIONS, LocalDateTime.now());
            if (tasks > 0 || sessions > 0) {
                throw new IllegalArgumentException("Groom " + groom.getFullName() + " còn " + tasks + " việc chăm sóc và " + sessions
                        + " buổi tập chưa thực hiện ở " + oldZone + ". Hãy giao lại hoặc hủy các việc này trước khi "
                        + (newZone == null ? "bỏ phân công." : "đổi sang " + newZone + "."));
            }
        }
        return new ZoneChange(groom, oldZone, newZone);
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }

    private record ZoneChange(User groom, String oldZone, String newZone) {
    }

    private User findGroom(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên với ID: " + userId));
        if (!"Groom".equals(user.getRoleName())) {
            throw new IllegalArgumentException("Chỉ phân công khu cho nhân viên có vai trò Groom (người được chọn có vai trò " + user.getRoleName() + ").");
        }
        return user;
    }

    private static String normalize(String zone) {
        return zone == null ? null : zone.trim().toLowerCase();
    }

    private static GroomZoneResponseDTO toDTO(User groom, String zone) {
        return GroomZoneResponseDTO.builder()
                .userId(groom.getUserId())
                .fullName(groom.getFullName())
                .barnZone(zone)
                .build();
    }
}
