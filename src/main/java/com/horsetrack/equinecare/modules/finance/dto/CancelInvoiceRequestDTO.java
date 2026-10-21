package com.horsetrack.equinecare.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelInvoiceRequestDTO {

    @NotBlank(message = "Phải nhập lý do hủy hóa đơn")
    @Size(min = 10, max = 255, message = "Lý do hủy từ 10 đến 255 ký tự")
    private String reason;
}
