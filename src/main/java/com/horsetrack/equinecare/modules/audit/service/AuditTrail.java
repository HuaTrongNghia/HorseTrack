package com.horsetrack.equinecare.modules.audit.service;

import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Ghi Audit Log cho các thao tác trọng yếu (BR_10 / GEN-07) mà không phải truyền userId, IP qua từng hàm.
 * Người thực hiện lấy từ người đang đăng nhập, IP lấy từ request hiện tại (nếu có).
 * Ghi log trong cùng transaction của nghiệp vụ: nghiệp vụ lỗi thì log cũng không được lưu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditTrail {

    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    /**
     * @param actionType  mã hành động, tối đa 20 ký tự (VD: APPROVE_HORSE, VET_LOCK)
     * @param targetTable bảng bị tác động (VD: Horses, Treatment_Protocols)
     * @param oldValue    trạng thái / giá trị trước (có thể null)
     * @param newValue    trạng thái / giá trị sau, kèm mã đối tượng và lý do
     */
    public void record(String actionType, String targetTable, String oldValue, String newValue) {
        auditLogService.log(currentUserId(), actionType, targetTable, oldValue, newValue, currentIp());
    }

    private Integer currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !StringUtils.hasText(auth.getName())) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).map(u -> u.getUserId()).orElse(null);
    }

    private String currentIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null; // Không có request (tác vụ định kỳ, test)
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        return StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
