package com.horsetrack.equinecare.modules.medical.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Ghi nhận đã tiêm (body tùy chọn) */
@Data
public class VaccinationExecuteDTO {

    // Ngày thực hiện yyyy-MM-dd hoặc dd/MM/yyyy; bỏ trống = hôm nay; không được sau hôm nay
    private String doneAt;

    @Size(max = 50, message = "Số lô tối đa 50 ký tự")
    private String lotNo;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
