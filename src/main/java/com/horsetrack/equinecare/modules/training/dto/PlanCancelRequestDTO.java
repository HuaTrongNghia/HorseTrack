package com.horsetrack.equinecare.modules.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** HLV phụ trách / Club Manager hủy giáo án */
@Data
public class PlanCancelRequestDTO {

    @NotBlank(message = "Phải ghi lý do hủy giáo án")
    @Size(min = 5, max = 500, message = "Lý do hủy từ 5 đến 500 ký tự")
    private String reason;
}
