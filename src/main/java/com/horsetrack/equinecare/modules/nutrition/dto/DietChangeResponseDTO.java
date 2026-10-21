package com.horsetrack.equinecare.modules.nutrition.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DietChangeResponseDTO {

    private Integer requestId;
    private String requestCode;
    private Integer horseId;
    private String horseName;
    private Integer requestedById;
    private String requestedByName;
    private Integer itemId;
    private String itemCode;
    private String itemName;
    private String item; // Alias cho itemName hoặc requestDetail
    private String requestDetail;
    private String reason;
    private LocalDateTime requestedAt;
    private String status;
    private Integer decidedById;
    private String decidedByName;
    private LocalDateTime decidedAt;
    private String vetNote;
    private Integer nutritionPlanId;
}
