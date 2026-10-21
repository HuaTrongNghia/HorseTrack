package com.horsetrack.equinecare.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceGenerateResultDTO {
    private String billingMonth;
    private int created;
    private int skippedExisting;   // Ngựa đã có hóa đơn kỳ này
    private int skippedZero;       // Ngựa không phát sinh chi phí (tổng 0 đồng)
    private List<String> invoiceCodes;
}
