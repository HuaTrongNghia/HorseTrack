package com.horsetrack.equinecare.modules.training.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerHorseListDTO {
    private Integer horseId;
    private String horseName;
}
