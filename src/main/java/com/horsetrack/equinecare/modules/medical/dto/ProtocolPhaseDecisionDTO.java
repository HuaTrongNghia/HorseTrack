package com.horsetrack.equinecare.modules.medical.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Bác sĩ kết thúc sớm hoặc bắt đầu sớm một giai đoạn phác đồ. Ghi chú không bắt buộc. */
@Data
public class ProtocolPhaseDecisionDTO {

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    private String note;
}
