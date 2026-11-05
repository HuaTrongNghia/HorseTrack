package com.horsetrack.equinecare.modules.notification.service;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.notification.dto.EmailLogDTO;
import com.horsetrack.equinecare.modules.notification.entity.EmailLog;
import com.horsetrack.equinecare.modules.notification.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** F1, F2: ghi và tra cứu nhật ký gửi email */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailLogService {

    private static final int BOUNCE_WINDOW_DAYS = 3;

    private final EmailLogRepository logRepository;
    private final UserRepository userRepository;

    /**
     * Ghi một lần gửi. Không bao giờ ném lỗi ra ngoài: ghi nhật ký thất bại (ví dụ chưa chạy DATABASE_email_logs.sql)
     * không được làm hỏng việc gửi email.
     */
    public void record(String toEmail, String purpose, String subject, String status, String error) {
        try {
            Integer userId = StringUtils.hasText(toEmail) ? userRepository.findByEmail(toEmail.trim()).map(User::getUserId).orElse(null) : null;
            logRepository.save(EmailLog.builder()
                    .toEmail(truncate(toEmail, 255))
                    .userId(userId)
                    .purpose(truncate(StringUtils.hasText(purpose) ? purpose : "GENERAL", 40))
                    .subject(truncate(subject, 255))
                    .status(status)
                    .error(truncate(error, 1000))
                    .sentAt(LocalDateTime.now())
                    .build());
        } catch (Exception ex) {
            log.warn("Không ghi được nhật ký email tới {} ({}): {}", toEmail, status, ex.getMessage());
        }
    }

    /**
     * Thư bị trả về: chuyển dòng SENT gần nhất tới địa chỉ này sang FAILED, để nhật ký không có cả SENT lẫn FAILED cho một email.
     * Không tìm thấy dòng SENT (đã quá lâu, hoặc email gửi trước khi có nhật ký) thì ghi một dòng FAILED mới.
     */
    public void markBounced(String toEmail, String fallbackPurpose, String subject, String error) {
        try {
            var sent = logRepository.findFirstByToEmailIgnoreCaseAndStatusAndSentAtAfterOrderByLogIdDesc(
                    toEmail, EmailLog.SENT, LocalDateTime.now().minusDays(BOUNCE_WINDOW_DAYS));
            if (sent.isPresent()) {
                EmailLog entry = sent.get();
                entry.setStatus(EmailLog.FAILED);
                entry.setError(truncate(error, 1000));
                logRepository.save(entry);
                return;
            }
        } catch (Exception ex) {
            log.warn("Không cập nhật được nhật ký email tới {}: {}", toEmail, ex.getMessage());
        }
        record(toEmail, fallbackPurpose, subject, EmailLog.FAILED, error);
    }

    @Transactional(readOnly = true)
    public Page<EmailLogDTO> search(Integer userId, String purpose, String status, String toEmail, LocalDate fromDate, LocalDate toDate, int page, int size) {
        String st = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        if (st != null && !Set.of(EmailLog.SENT, EmailLog.FAILED, EmailLog.SKIPPED).contains(st)) {
            throw new IllegalArgumentException("status chỉ nhận SENT, FAILED hoặc SKIPPED.");
        }
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate không được trước fromDate.");
        }
        Page<EmailLog> logs = logRepository.search(userId,
                StringUtils.hasText(purpose) ? purpose.trim().toUpperCase() : null,
                st,
                StringUtils.hasText(toEmail) ? "%" + toEmail.trim().toLowerCase() + "%" : null,
                fromDate != null ? fromDate.atStartOfDay() : null,
                toDate != null ? toDate.plusDays(1).atStartOfDay() : null,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        Map<Integer, String> names = new HashMap<>();
        userRepository.findAllById(logs.getContent().stream().map(EmailLog::getUserId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .forEach(u -> names.put(u.getUserId(), u.getFullName()));
        return logs.map(e -> EmailLogDTO.builder()
                .logId(e.getLogId())
                .toEmail(e.getToEmail())
                .userId(e.getUserId())
                .userFullName(e.getUserId() != null ? names.get(e.getUserId()) : null)
                .purpose(e.getPurpose())
                .subject(e.getSubject())
                .status(e.getStatus())
                .error(e.getError())
                .sentAt(e.getSentAt())
                .build());
    }

    private static String truncate(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
