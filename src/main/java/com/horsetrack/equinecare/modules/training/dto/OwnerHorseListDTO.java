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
    private String approvalStatus; // PENDING: chờ Club Manager duyệt; APPROVED: đã duyệt và cấp chuồng
    private Integer stallId;
    private String stallCode;
}
