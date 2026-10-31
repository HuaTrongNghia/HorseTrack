package com.horsetrack.equinecare.modules.tournament.service;

import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.notification.service.AppNotificationService;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import com.horsetrack.equinecare.modules.tournament.entity.CompetitionEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Nhãn trạng thái, thông báo (chuông + email) và nhật ký hệ thống cho luồng giải đấu */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaceNotifier {

    public static final Map<String, String> STATUS_LABELS = Map.ofEntries(
            Map.entry(CompetitionEntry.WAITLISTED, "Trong hàng chờ"),
            Map.entry(CompetitionEntry.WAITING_TRAINER, "Giữ chỗ - chờ HLV xác nhận"),
            Map.entry(CompetitionEntry.TRAINER_REJECTED, "HLV từ chối"),
            Map.entry(CompetitionEntry.TRAINER_EXPIRED, "Quá hạn HLV xác nhận"),
            Map.entry(CompetitionEntry.WAITING_VET_EXAM, "Giữ chỗ - chờ bác sĩ khám"),
            Map.entry(CompetitionEntry.VET_FAILED, "Khám không đạt"),
            Map.entry(CompetitionEntry.VET_EXPIRED, "Quá hạn khám"),
            Map.entry(CompetitionEntry.WAITING_PAYMENT, "Giữ chỗ - chờ thanh toán lệ phí"),
            Map.entry(CompetitionEntry.PAYMENT_EXPIRED, "Quá hạn thanh toán"),
            Map.entry(CompetitionEntry.REGISTERED, "Đã đăng ký thi đấu"),
            Map.entry(CompetitionEntry.RESULT_RECORDED, "Đã có kết quả"),
            Map.entry(CompetitionEntry.CANCELLED, "Chủ ngựa đã hủy"),
            Map.entry(CompetitionEntry.INELIGIBLE, "Ngựa không còn đủ điều kiện"),
            Map.entry(CompetitionEntry.NO_SLOT, "Hết suất (hết hạn đăng ký)"));

    /** Mã lý do HLV từ chối (bắt buộc chọn khi từ chối) */
    public static final Map<String, String> REJECT_REASONS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("NOT_FIT", "Thể lực chưa đạt yêu cầu thi đấu");
        m.put("INJURY_RISK", "Nguy cơ chấn thương / cần theo dõi sức khỏe");
        m.put("POOR_FORM", "Phong độ gần đây chưa tốt");
        m.put("TRAINING_PLAN", "Không kịp hoặc xung đột giáo án huấn luyện");
        m.put("DISTANCE_MISMATCH", "Cự ly giải không phù hợp với ngựa");
        m.put("OTHER", "Lý do khác (ghi rõ trong ghi chú)");
        REJECT_REASONS = java.util.Collections.unmodifiableMap(m);
    }

    static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AppNotificationService appNotifications;
    private final AuditTrail auditTrail;

    public static String label(String status) {
        return STATUS_LABELS.getOrDefault(status, status);
    }

    public static String esc(String s) {
        return HtmlUtils.htmlEscape(String.valueOf(s));
    }

    public static String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(DT);
    }

    /** Thông báo trên chuông và email (lỗi gửi email không làm hỏng nghiệp vụ) */
    public void notify(User to, String subject, String html) {
        notify(to, subject, html, linkFor(to));
    }

    /** Như notify nhưng chỉ định trang mở khi bấm thông báo (VD: giải mới -> /owner/tournaments) */
    public void notify(User to, String subject, String html, String link) {
        if (to == null) {
            return;
        }
        String title = subject != null && subject.startsWith("[HorseTrack] ") ? subject.substring(13) : subject;
        String plain = html == null ? title : HtmlUtils.htmlUnescape(html.replaceAll("<[^>]+>", " ")).replaceAll("\\s+", " ").trim();
        appNotifications.push(to, "RACE_PROPOSAL", title, plain, link);
        if (!StringUtils.hasText(to.getEmail())) {
            return;
        }
        try {
            emailService.sendAsync("RACE_PROPOSAL", to.getEmail(), subject, "<p>Xin chào <b>" + esc(to.getFullName()) + "</b>,</p>" + html);
        } catch (Exception ex) {
            log.warn("Không gửi được email giải đấu: {}", ex.getMessage());
        }
    }

    /** Thông báo cho mọi tài khoản đang hoạt động của một vai trò (VD: tất cả bác sĩ, quản lý CLB) */
    public void notifyRole(String roleName, String subject, String html) {
        List<User> users = userRepository.findByRoleNameInAndIsActiveTrue(List.of(roleName));
        users.forEach(u -> notify(u, subject, html));
    }

    public void notifyRole(String roleName, String subject, String html, String link) {
        userRepository.findByRoleNameInAndIsActiveTrue(List.of(roleName)).forEach(u -> notify(u, subject, html, link));
    }

    /** Nhật ký thay đổi trạng thái đề xuất; system = true khi hệ thống tự hủy / tự chuyển */
    public void audit(String action, String before, CompetitionEntry e, String extra) {
        auditTrail.record(action, "Competition_Entries", before == null ? null : "status=" + before,
                "status=" + e.getStatus() + " | đề xuất " + code(e) + " | ngựa " + e.getHorse().getHorseName()
                        + " | giải " + e.getCompetition().getCompetitionName() + (StringUtils.hasText(extra) ? " | " + extra : ""));
    }

    public static String code(CompetitionEntry e) {
        return e.getEntryCode() != null ? e.getEntryCode() : "#" + e.getEntryId();
    }

    /** Trang xử lý đề xuất thi đấu theo vai trò người nhận */
    public static String linkFor(User to) {
        String role = to.getRoleName() == null ? "" : to.getRoleName();
        return switch (role) {
            case "Horse Owner" -> "/owner/race-entries";
            case "Trainer" -> "/trainer/race-confirmations";
            case "Veterinarian" -> "/vet/race-exams";
            case "Club Manager" -> "/manager/race-complaints";
            default -> null;
        };
    }
}
