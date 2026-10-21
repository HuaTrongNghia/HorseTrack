package com.horsetrack.equinecare.modules.audit.service.impl;

import com.horsetrack.equinecare.modules.audit.dto.AuditLogResponseDTO;
import com.horsetrack.equinecare.modules.audit.entity.AuditLog;
import com.horsetrack.equinecare.modules.audit.repository.AuditLogRepository;
import com.horsetrack.equinecare.modules.audit.service.AuditLogService;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void log(Integer userId, String actionType, String targetTable, String oldValue, String newValue, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .actionType(actionType != null && actionType.length() > 20 ? actionType.substring(0, 20) : actionType)
                .targetTable(targetTable != null && targetTable.length() > 50 ? targetTable.substring(0, 50) : targetTable)
                .oldValue(oldValue)
                .newValue(newValue)
                .ipAddress(ipAddress != null && ipAddress.length() > 45 ? ipAddress.substring(0, 45) : ipAddress)
                .timestamp(LocalDateTime.now())
                .build();
        auditLogRepository.save(auditLog);
    }

    private AuditLogResponseDTO mapToDTO(AuditLog log, Map<Integer, User> userMap) {
        User u = log.getUserId() != null ? userMap.get(log.getUserId()) : null;
        return AuditLogResponseDTO.builder()
                .logId(log.getLogId())
                .userId(log.getUserId())
                .username(u != null ? u.getUsername() : null)
                .fullName(u != null ? u.getFullName() : null)
                .actionType(log.getActionType())
                .targetTable(log.getTargetTable())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .ipAddress(log.getIpAddress())
                .timestamp(log.getTimestamp())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponseDTO> getRecentLogs() {
        List<AuditLog> logs = auditLogRepository.findTop100ByOrderByTimestampDesc();
        Map<Integer, User> userMap = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getUserId, u -> u, (u1, u2) -> u1));
        return logs.stream().map(l -> mapToDTO(l, userMap)).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponseDTO> getLogsPaged(int page, int size) {
        Page<AuditLog> logPage = auditLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(page, size));
        Map<Integer, User> userMap = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getUserId, u -> u, (u1, u2) -> u1));
        return logPage.map(l -> mapToDTO(l, userMap));
    }
}
