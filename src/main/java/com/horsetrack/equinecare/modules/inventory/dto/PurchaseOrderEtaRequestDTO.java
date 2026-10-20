package com.horsetrack.equinecare.modules.inventory.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderEtaRequestDTO {

    @NotNull(message = "Ngày dự kiến hàng về mới (expectedDeliveryDate) là bắt buộc")
    @FutureOrPresent(message = "Ngày dự kiến hàng về không được ở trong quá khứ")
    private LocalDate expectedDeliveryDate;

    @Size(max = 200, message = "Lý do đổi ngày (note) không được vượt quá 200 ký tự")
    private String note;
}
