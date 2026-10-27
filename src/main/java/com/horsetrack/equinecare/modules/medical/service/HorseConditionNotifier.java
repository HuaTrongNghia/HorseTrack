package com.horsetrack.equinecare.modules.medical.service;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * Báo cho chủ ngựa khi ngựa bị chấn thương, bệnh hoặc cấp cứu.
 * Ngựa cấp cứu thì phác đồ đang chờ thanh toán được mở để kho cấp thuốc và vật tư ngay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HorseConditionNotifier {

    private final EmailService emailService;
    private final ProtocolPhaseBilling phaseBilling;

    public void onStatus(Horse horse, String status, String detail) {
        if (horse == null || !StringUtils.hasText(status)) {
            return;
        }
        String code = status.trim().toUpperCase();
        if ("EMERGENCY".equals(code)) {
            phaseBilling.releaseForEmergencyHorse(horse.getHorseId());
        }
        String what = switch (code) {
            case "INJURED" -> "bị chấn thương";
            case "QUARANTINE" -> "đang cách ly vì bệnh";
            case "EMERGENCY" -> "đang cấp cứu";
            default -> null;
        };
        if (what != null) {
            notify(horse, what, detail);
        }
    }

    public void onInjury(Horse horse, String part, String severityLabel, String note) {
        StringBuilder detail = new StringBuilder();
        detail.append("Vị trí: ").append(part == null ? "" : part);
        if (StringUtils.hasText(severityLabel)) {
            detail.append("\nMức độ: ").append(severityLabel);
        }
        if (StringUtils.hasText(note)) {
            detail.append("\nGhi chú: ").append(note.trim());
        }
        notify(horse, "bị chấn thương", detail.toString());
    }

    public void onIllness(Horse horse, String diagnosis, String symptoms, String extra) {
        StringBuilder detail = new StringBuilder();
        if (StringUtils.hasText(diagnosis)) {
            detail.append("Chẩn đoán: ").append(diagnosis.trim());
        }
        if (StringUtils.hasText(symptoms)) {
            if (!detail.isEmpty()) {
                detail.append("\n");
            }
            detail.append("Triệu chứng: ").append(symptoms.trim());
        }
        if (StringUtils.hasText(extra)) {
            if (!detail.isEmpty()) {
                detail.append("\n");
            }
            detail.append(extra.trim());
        }
        String what = StringUtils.hasText(diagnosis) ? "được chẩn đoán: " + diagnosis.trim() : "đang được ghi nhận bệnh";
        notify(horse, what, detail.toString());
    }

    private void notify(Horse horse, String what, String detail) {
        User owner = horse.getOwner();
        if (owner == null || !StringUtils.hasText(owner.getEmail())) {
            log.warn("Không gửi email sức khỏe: ngựa {} chưa có email chủ.", horse.getHorseId());
            return;
        }
        String horseName = horse.getHorseName();
        String subjectWhat = what.length() > 80 ? what.substring(0, 80) : what;
        String subject = "[HorseTrack] Ngựa " + horseName + " " + subjectWhat;
        if (subject.length() > 200) {
            subject = subject.substring(0, 200);
        }
        String detailHtml = StringUtils.hasText(detail)
                ? "<p>" + HtmlUtils.htmlEscape(detail).replace("\n", "<br>") + "</p>"
                : "";
        String body = """
                <div style="font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#222;max-width:560px">
                  <h2 style="color:#2f5d50;margin-bottom:8px">HorseTrack EquineCare</h2>
                  <p>Xin chào <b>%s</b>,</p>
                  <p>Ngựa <b>%s</b> %s.</p>
                  %s
                  <p>Vui lòng theo dõi và liên hệ bác sĩ thú y của câu lạc bộ nếu cần thêm thông tin.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(owner.getFullName()), HtmlUtils.htmlEscape(horseName),
                HtmlUtils.htmlEscape(what), detailHtml);
        try {
            emailService.sendAsync("HORSE_HEALTH", owner.getEmail(), subject, body);
        } catch (Exception ex) {
            log.warn("Không gửi được email sức khỏe tới {}: {}", owner.getEmail(), ex.getMessage());
        }
    }
}
