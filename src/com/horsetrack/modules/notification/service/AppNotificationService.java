package com.horsetrack.equinecare.modules.notification.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.notification.dto.AppNotificationDTO;
import com.horsetrack.equinecare.modules.notification.entity.AppNotification;
import com.horsetrack.equinecare.modules.notification.repository.AppNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Chuông in-app: chỉ ghi theo userId cụ thể.
 * Không gửi OTP / STAFF_ACCOUNT / ACCOUNT_LOCKED vào đây (chỉ email).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppNotificationService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int TITLE_MAX = 255;
    private static final int MSG_MAX = 1000;

    private final AppNotificationRepository repository;
    private final UserRepository userRepository;

    /** Đẩy 1 thông báo cho đúng user; bỏ qua nếu user null. */
    @Transactional
    public void push(User user, String purpose, String title, String message, String link) {
        if (user == null || user.getUserId() == null) {
            return;
        }
        try {
            repository.save(AppNotification.builder()
                    .userId(user.getUserId())
                    .purpose(purpose == null ? "GENERAL" : purpose)
                    .title(clip(title, TITLE_MAX))
                    .message(clip(message == null ? "" : message, MSG_MAX))
                    .link(StringUtils.hasText(link) ? clip(link, 255) : null)
                    .read(false)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception ex) {
            log.warn("Không ghi được thông báo {} cho user {}: {}", purpose, user.getUserId(), ex.getMessage());
        }
    }

    @Transactional
    public void push(User user, String purpose, String title, String message) {
        push(user, purpose, title, message, null);
    }

    @Transactional
    public void pushAll(Collection<User> users, String purpose, String title, String message, String link) {
        if (users == null) {
            return;
        }
        users.stream().filter(u -> u != null && u.getUserId() != null)
                .forEach(u -> push(u, purpose, title, message, link));
    }

    @Transactional(readOnly = true)
    public Page<AppNotificationDTO> mine(int page, int size) {
        User me = currentUser();
        int safeSize = Math.min(Math.max(size, 1), 50);
        return repository.findByUserIdOrderByCreatedAtDesc(me.getUserId(), PageRequest.of(Math.max(page, 0), safeSize))
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByUserIdAndReadFalse(currentUser().getUserId());
    }

    @Transactional
    public void markRead(Long id) {
        Integer userId = currentUser().getUserId();
        int n = repository.markRead(id, userId);
        if (n == 0) {
            boolean mine = repository.findById(id).filter(row -> userId.equals(row.getUserId())).isPresent();
            if (!mine) {
                throw new ResourceNotFoundException("Không tìm thấy thông báo #" + id);
            }
        }
    }

    @Transactional
    public int markAllRead() {
        return repository.markAllRead(currentUser().getUserId());
    }

    private AppNotificationDTO toDto(AppNotification n) {
        return AppNotificationDTO.builder()
                .id(n.getNotificationId())
                .purpose(n.getPurpose())
                .title(n.getTitle())
                .message(n.getMessage())
                .link(n.getLink())
                .read(n.isRead())
                .createdAt(n.getCreatedAt() == null ? null : WHEN.format(n.getCreatedAt()))
                .build();
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
