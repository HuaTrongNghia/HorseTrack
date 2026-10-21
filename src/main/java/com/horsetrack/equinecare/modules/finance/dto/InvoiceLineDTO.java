package com.horsetrack.equinecare.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceLineDTO {
    private Integer lineItemId;
    private String category;        // BOARDING, DAILY_CARE, MEDICATION, COMPETITION...
    private String categoryLabel;   // Tên tiếng Việt
    private Integer referenceId;
    private String description;
    private BigDecimal unitPrice;
    private BigDecimal quantity;
    private BigDecimal amount;
    private LocalDateTime serviceDate;
}
