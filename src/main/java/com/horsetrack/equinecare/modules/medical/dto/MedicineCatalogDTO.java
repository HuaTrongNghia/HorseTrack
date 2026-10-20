package com.horsetrack.equinecare.modules.medical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Một dòng trong danh mục thuốc (chỉ đọc) cho bác sĩ chọn khi lập phác đồ. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicineCatalogDTO {
    private Integer itemId;
    private String itemCode;
    private String itemName;
    private String category;
    private String unit;
    private BigDecimal unitPrice;
    private Double availableStock;
    private String route;
    private Boolean isControlled;
    private Integer withdrawalHours; // Thời gian phát hiện/ngưng thuốc trước khi thi đấu
    private String doseReference;
}
