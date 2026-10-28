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
    private String approverRole;   // người xét duyệt theo BR_03: Trainer (ngựa không khóa) hoặc Veterinarian (ngựa đang khóa huấn luyện)
    private String approverName;   // HLV phụ trách ngựa, hoặc "Bác sĩ thú y"
    private Boolean canDecide;     // người đang đăng nhập được duyệt / từ chối đề xuất này
}
