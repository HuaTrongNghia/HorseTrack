package com.horsetrack.equinecare.modules.medical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalCaseDTO {
    private String caseId;       // Mã ca do DB sinh (VD: C-0001); dùng làm caseId khi gọi /cases/action
    private Integer id;          // ID số của ca (case_id)
    private Integer horseId;
    private String horseName;
    private String source;       // GROOM_REPORT, TRAINER_REQUEST, SESSION_STOP, OWNER_REQUEST, ROUTINE
    private String issueType;    // Loại sự cố (Đi khập khiễng, Sốt...)
    private String severity;     // LOW, MEDIUM, HIGH, CRITICAL
    private String status;       // NEW, IN_EXAM, MONITORING, CLOSED, CANCELLED
    private String description;  // Mô tả chi tiết
    private Integer reportedById;
    private String reportedByName;
    private String assignedVetName;
    private Integer maxHeartRate; // Dành cho ca từ phiên tập bị dừng (chưa dùng)
    private Integer maxSpeed;
    private String vetResult;     // Kết quả khám / kết luận của bác sĩ
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
}
