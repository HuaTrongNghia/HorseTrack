package com.horsetrack.equinecare.modules.tournament.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Chủ ngựa khiếu nại việc HLV từ chối đề xuất */
@Data
public class ComplaintDTO {

    @NotBlank(message = "Phải ghi nội dung khiếu nại")
    @Size(max = 500, message = "Nội dung khiếu nại tối đa 500 ký tự")
    private String note;
}
