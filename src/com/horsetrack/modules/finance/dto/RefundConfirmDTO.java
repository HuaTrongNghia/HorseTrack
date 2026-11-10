package com.horsetrack.equinecare.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** Quản lý CLB xác nhận đã chuyển tiền hoàn cho chủ ngựa */
@Data
public class RefundConfirmDTO {

    @NotBlank(message = "Mã giao dịch chuyển tiền là bắt buộc")
    @Size(max = 100, message = "Mã giao dịch tối đa 100 ký tự")
    private String transactionRef;

    @NotNull(message = "Ngày chuyển là bắt buộc")
    @PastOrPresent(message = "Ngày chuyển không được ở tương lai")
    private LocalDate transferredOn;

    @NotBlank(message = "Người chuyển là bắt buộc")
    @Size(max = 150, message = "Người chuyển tối đa 150 ký tự")
    private String transferredBy;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
