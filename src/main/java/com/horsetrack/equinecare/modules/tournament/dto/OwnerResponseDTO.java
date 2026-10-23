package com.horsetrack.equinecare.modules.tournament.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OwnerResponseDTO {

    @NotNull(message = "Phải chọn đồng ý hoặc từ chối (agree)")
    private Boolean agree;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
