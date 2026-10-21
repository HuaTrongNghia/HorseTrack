package com.horsetrack.equinecare.modules.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponseDTO {
    private Integer logId;
    private Integer userId;
    private String username;
    private String fullName;
    private String actionType;
    private String targetTable;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private LocalDateTime timestamp;
}
