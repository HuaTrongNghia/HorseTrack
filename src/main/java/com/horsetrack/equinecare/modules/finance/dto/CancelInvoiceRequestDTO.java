package com.horsetrack.equinecare.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelInvoiceRequestDTO {

    @NotBlank(message = "Phải nhập lý do hủy hóa đơn")
    @Size(max = 255, message = "Lý do hủy không được vượt quá 255 ký tự")
    private String reason;
}
