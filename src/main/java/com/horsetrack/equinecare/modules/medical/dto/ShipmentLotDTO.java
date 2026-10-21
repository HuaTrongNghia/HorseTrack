package com.horsetrack.equinecare.modules.medical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentLotDTO {
    private String lotCode;
    private String itemName;
    private Integer quantity;
    private String unit;
    private String eta;
    private String targetProtocol;
    private String status;
}
