package com.horsetrack.equinecare.modules.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Groom gửi yêu cầu vật tư (không phải thuốc/vaccine) cho kho. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplyRequestCreateDTO {

    @NotNull(message = "Phải chọn vật tư (itemId)")
    private Integer itemId;

    @NotNull(message = "Số lượng (quantity) là bắt buộc")
    @Positive(message = "Số lượng phải lớn hơn 0")
    @Digits(integer = 8, fraction = 2, message = "Số lượng tối đa 2 chữ số thập phân")
    private Double quantity;

    @NotBlank(message = "Lý do (reason) là bắt buộc")
    @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;

    @FutureOrPresent(message = "Ngày cần (neededBy) không được ở trong quá khứ")
    private LocalDate neededBy;
}
