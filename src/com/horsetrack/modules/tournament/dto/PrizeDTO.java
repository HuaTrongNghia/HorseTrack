package com.horsetrack.equinecare.modules.tournament.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Một dòng bảng thưởng: hạng -> tiền thưởng */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrizeDTO {

    @NotNull(message = "Hạng là bắt buộc") @Min(value = 1, message = "Hạng từ 1 trở lên") @Max(value = 100, message = "Hạng tối đa 100")
    private Integer rank;

    @NotNull(message = "Tiền thưởng là bắt buộc") @DecimalMin(value = "0", message = "Tiền thưởng không được âm")
    private BigDecimal amount;
}
