package com.horsetrack.equinecare.modules.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GroomZoneRequestDTO {

    // Tên khu (ví dụ "Khu B"); null hoặc rỗng để bỏ phân công
    @Size(max = 50, message = "Tên khu tối đa 50 ký tự")
    private String barnZone;
}
