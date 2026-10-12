package com.horsetrack.equinecare.modules.auth.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponseDTO {
    private Integer userId;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String roleName;
    private Boolean isActive;

    private String specialization;
    private String licenseNumber;
    private Integer experienceYears;
    private String clinicAffiliation;
}
