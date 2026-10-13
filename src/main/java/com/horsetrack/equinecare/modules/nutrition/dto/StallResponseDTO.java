package com.horsetrack.equinecare.modules.nutrition.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StallResponseDTO {
    private Integer stallId;
    private String stallCode;
    private String barnZone;
    private String cleanStatus;
    private Integer capacity;
    private Integer currentHorseId;
    private String horseName;
    private Boolean isLocked;
    private String horseHealthStatus;
}
