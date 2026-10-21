package com.horsetrack.equinecare.modules.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Kho cấp vật tư cho yêu cầu của Groom. Mọi field đều tùy chọn. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplyRequestIssueDTO {

    // Bỏ trống thì cấp đủ số lượng đã yêu cầu; được cấp ít hơn nhưng không nhiều hơn
    @Positive(message = "Số lượng cấp phải lớn hơn 0")
    @Digits(integer = 8, fraction = 2, message = "Số lượng tối đa 2 chữ số thập phân")
    private Double issuedQuantity;

    // Lô xuất (tùy chọn)
    @Size(max = 50, message = "Số lô (lotNo) không được vượt quá 50 ký tự")
    private String lotNo;

    @Size(max = 500, message = "Ghi chú (note) không được vượt quá 500 ký tự")
    private String note;
}
