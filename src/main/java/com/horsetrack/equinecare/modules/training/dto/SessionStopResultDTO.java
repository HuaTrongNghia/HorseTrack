package com.horsetrack.equinecare.modules.training.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Kết quả dừng buổi tập: trạng thái mới và mã ca khám nếu đã gửi bác sĩ. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStopResultDTO {
    private String status;
    private String caseCode;
}
