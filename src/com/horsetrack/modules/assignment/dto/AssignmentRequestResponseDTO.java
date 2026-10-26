package com.horsetrack.equinecare.modules.assignment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssignmentRequestResponseDTO {
    private Integer requestId;
    private String requestType;
    private String status;
    private Integer targetUserId;
    private String targetName;
    private Integer horseId;
    private String horseName;
    private Integer previousUserId;
    private String previousName;
    private String barnZone;
    private String previousZone;
    private String createdAt;
}
