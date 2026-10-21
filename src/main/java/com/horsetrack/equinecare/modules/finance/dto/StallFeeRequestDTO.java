package com.horsetrack.equinecare.modules.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class StallFeeRequestDTO {

    @NotNull(message = "monthlyBaseFee không được để trống")
    @DecimalMin(value = "0", message = "Phí lưu chuồng không được âm")
    @Digits(integer = 10, fraction = 0, message = "Phí lưu chuồng là số nguyên VND, tối đa 10 chữ số")
    private BigDecimal monthlyBaseFee;
}
