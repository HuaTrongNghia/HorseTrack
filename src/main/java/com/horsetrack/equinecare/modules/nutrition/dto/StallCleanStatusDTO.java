package com.horsetrack.equinecare.modules.nutrition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StallCleanStatusDTO {

    @NotBlank(message = "Phải chọn trạng thái chuồng")
    @Pattern(regexp = "(?i)^(CLEAN|NEEDS_CLEANING|MAINTENANCE)$", message = "status chỉ nhận CLEAN, NEEDS_CLEANING, MAINTENANCE")
    private String status;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
