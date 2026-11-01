package com.horsetrack.equinecare.modules.tournament.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** HLV xác nhận / từ chối đề xuất đang giữ chỗ; từ chối bắt buộc chọn reasonCode (GET /race-proposals/reject-reasons) */
@Data
public class TrainerDecisionDTO {

    @NotNull(message = "Phải chọn xác nhận hoặc từ chối (approve)")
    private Boolean approve;

    @Size(max = 30, message = "Mã lý do tối đa 30 ký tự")
    private String reasonCode;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
