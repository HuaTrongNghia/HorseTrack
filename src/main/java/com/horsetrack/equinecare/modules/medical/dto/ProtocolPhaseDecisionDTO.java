package com.horsetrack.equinecare.modules.medical.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Bác sĩ kết thúc hoặc bắt đầu một giai đoạn phác đồ. Ghi chú không bắt buộc, trừ khi ngưng phác đồ. */
@Data
public class ProtocolPhaseDecisionDTO {

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    private String note;

    /**
     * Dùng khi hoàn thành giai đoạn. true hoặc bỏ trống: chuyển giai đoạn sau nếu đã thanh toán, hoặc kết thúc nếu hết giai đoạn.
     * false: ngưng phác đồ sau giai đoạn này vì lý do chuyên môn. Khi đó ghi chú là bắt buộc.
     */
    private Boolean continueTreatment;
}
