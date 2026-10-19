package com.horsetrack.dto;

import lombok.Data;

@Data
public class ShipmentLotDTO {
    private String lotCode;        // VD: "SHIP-0024"
    private String itemName;       // VD: "Bông gạc"
    private Integer quantity;      // VD: 50
    private String unit;           // VD: "cuộn"
    private String eta;            // Ngày cập bến dự kiến
    private String targetProtocol; // Cho phác đồ nào (VD: "PD-0011")
    private String status;         // "Trễ", "Đang vận chuyển"
}
