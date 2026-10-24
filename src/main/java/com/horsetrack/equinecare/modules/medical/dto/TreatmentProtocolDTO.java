package com.horsetrack.equinecare.modules.medical.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TreatmentProtocolDTO {
    private Integer protocolId;
    private String protocolCode;
    private Integer horseId;
    private String horseName;
    private Integer vetId;
    private String vetName;
    private Integer caseId;
    private Integer recordId;
    private Integer versionNo;
    private Integer replacesProtocolId;   // Phiên bản trước
    private String replacesProtocolCode;
    private Integer replacedByProtocolId; // Phiên bản sau (nếu đã bị thay thế)
    private String replacedByProtocolCode;
    private String diagnosis;
    private String state;
    private LocalDate requiredBy;
    private Boolean isDelayed;
    private Boolean trainingLocked;
    private LocalDate waitEta;
    private String waitNote;
    private String note;
    private String outcome;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime closedAt;
    private BigDecimal totalCost;
    private Boolean emergency;            // Ca cấp cứu (CRITICAL): không bị tạm dừng vì chưa thanh toán (BR_17)
    private Boolean pendingSupplement;    // Đang điều trị và có thuốc bổ sung chờ kho cấp
    private List<Phase> phases;           // Chỉ có ở API chi tiết
    private List<StockAnswer> stockAnswer; // Kết quả kiểm kho của kho (chỉ có ở API chi tiết)
    private List<Note> progressNotes;     // Chỉ có ở API chi tiết

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Phase {
        private Integer phaseId;
        private Integer phaseNo;
        private String phaseName;
        private LocalDate startDate;
        private Integer durationDays;
        private String goal;
        // Hóa đơn của giai đoạn (BR_16): phải thanh toán trước ngày bắt đầu
        private Integer invoiceId;
        private String invoiceCode;
        private String invoiceStatus;
        private List<Item> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Integer protocolItemId;
        private Integer itemId;
        private String itemCode;
        private String itemName;
        private String unit;
        private Double quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private String dosage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockAnswer {
        private Integer holdId;
        private Integer itemId;
        private String itemName;
        private String unit;
        private Double qtyNeeded;
        private Double qtyAvailable;
        private Double qtyHeld;
        private Double qtyShort;
        private LocalDate eta;
        private Integer purchaseOrderId;
        private String status;       // HELD, SHORT, RELEASED, ISSUED
        private String checkedByName;
        private LocalDateTime checkedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Note {
        private Integer noteId;
        private String writtenByName;
        private LocalDateTime writtenAt;
        private String content;
    }
}
