package com.horsetrack.equinecare.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryLotDTO {
    private Integer lotId;
    private String lotNo;
    private LocalDate expiryDate;
    private Double quantity;
    private LocalDateTime receivedAt;
}
