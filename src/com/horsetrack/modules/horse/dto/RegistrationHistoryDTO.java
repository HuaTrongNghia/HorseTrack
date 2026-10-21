package com.horsetrack.equinecare.modules.horse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Một lần nộp hồ sơ của ngựa (mới nhất trước), dùng cho lịch sử hồ sơ. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationHistoryDTO {
    private Integer registrationId;
    private String registrationCode;
    private Integer horseId;
    private String horseName;
    private String ownerName;
    private LocalDateTime submittedAt;
    private String status;          // PENDING_REVIEW, APPROVED, REJECTED, CANCELLED
    private String ownerNote;       // Ghi chú khi nộp / nộp lại
    private String decisionNote;    // Lý do từ chối hoặc ghi chú phê duyệt
    private Boolean allowResubmit;
    private String decidedByName;
    private LocalDateTime decidedAt;
    private List<RegistrationCheckDTO> checks;
}
