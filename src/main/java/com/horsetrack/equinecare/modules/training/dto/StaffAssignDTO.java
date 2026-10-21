package com.horsetrack.equinecare.modules.training.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffAssignDTO {
    private Integer userId;
    private String fullName;
    private String roleName;
    private String barnZone; // Khu chuồng Groom phụ trách (trùng khu chuồng của ngựa trong giáo án)
}
