package com.horsetrack.equinecare.modules.tournament.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrganizerRejectDTO {

    @NotBlank(message = "Phải ghi lý do BTC từ chối")
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
