package com.horsetrack.equinecare.modules.tournament.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Quản lý CLB duyệt khiếu nại: chấp nhận thì đề xuất về WAITING_VET_EXAM và giữ nguyên suất */
@Data
public class ComplaintDecisionDTO {

    @NotNull(message = "Phải chọn chấp nhận hoặc bác khiếu nại (accept)")
    private Boolean accept;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
