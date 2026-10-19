package com.horsetrack.dto;

import lombok.Data;

@Data
public class ProtocolDTO {
    private String protocolCode;   // VD: "PD-0013 v1"
    private String horseName;      // VD: "Comet"
    private String diagnosis;      // VD: "Viêm móng nhẹ chân sau trái"
    private String caseCode;       // VD: "#C-0011"
    private String requiredDate;   // Ngày cần thuốc: "04/10/2026"
    private String estimatedCost;  // Chi phí dự kiến: "4.970.000 ₫"
    private String status;         // "Thiếu vật tư", "Kho đủ hàng", "Đang điều trị"
    private String nextStep;       // Bước tiếp theo
}
