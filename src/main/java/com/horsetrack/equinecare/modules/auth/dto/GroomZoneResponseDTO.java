package com.horsetrack.equinecare.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroomZoneResponseDTO {
    private Integer userId;
    private String fullName;
    private String barnZone;   // null = chưa được phân công khu
}
