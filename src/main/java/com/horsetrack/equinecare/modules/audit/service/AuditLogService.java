package com.horsetrack.equinecare.modules.audit.service;

import com.horsetrack.equinecare.modules.audit.dto.AuditLogResponseDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface AuditLogService {

    void log(Integer userId, String actionType, String targetTable, String oldValue, String newValue, String ipAddress);

    List<AuditLogResponseDTO> getRecentLogs();

    Page<AuditLogResponseDTO> getLogsPaged(int page, int size);
}
