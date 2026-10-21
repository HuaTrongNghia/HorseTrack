package com.horsetrack.equinecare.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerServiceDTO {
    private Integer ownerId;
    private String fullName;
    private String email;
    private String serviceStatus;
    private LocalDate suspendedSince;
    private String suspensionReason;
}
