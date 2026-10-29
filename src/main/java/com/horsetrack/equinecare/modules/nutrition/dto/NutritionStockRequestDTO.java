package com.horsetrack.equinecare.modules.nutrition.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Phiếu cấp thức ăn một ngày của một thực đơn (kho duyệt và cấp) */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionStockRequestDTO {

    private Integer planId;
    private String planType;          // STANDARD (HLV) / MEDICAL (bác sĩ)
    private String planStatus;
    private Integer horseId;
    private String horseName;
    private String stallCode;
    private String barnZone;
    private LocalDate date;           // ngày cần cấp
    private String createdByName;     // HLV / bác sĩ lập thực đơn
    private String status;            // PENDING (chờ cấp phát), ISSUED (đã cấp phát), CANCELLED (đã hủy / thay đổi)
    private Boolean canIssue;         // còn phiếu chờ cấp và đủ hàng khả dụng
    private LocalDateTime requestedAt;
    private LocalDateTime issuedAt;
    private String issuedByName;
    private List<Line> lines;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private Integer requisitionId;
        private String requisitionCode;
        private Integer itemId;
        private String itemCode;
        private String itemName;
        private String unit;
        private Double quantity;          // cần cấp cho ngày đó
        private Double availableStock;    // khả dụng hiện tại
        private Double issuedQuantity;
        private String status;            // SUBMITTED / ISSUED / CANCELLED ...
        private String note;              // lý do (kèm ghi chú hủy, nhập lại kho)
    }
}
