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
    private Boolean emergency;            // Cấp cứu (ngựa EMERGENCY hoặc ca CRITICAL): không chờ thanh toán mới cấp phát (BR_17)
    private Boolean pendingSupplement;    // Đang điều trị và có thuốc bổ sung chờ kho cấp
    private Integer awaitingPaymentPhaseNo;      // Đang chờ thanh toán (ON_HOLD_PAYMENT): giai đoạn nào (1 lúc mới cấp thuốc, 2+ sau khi kết thúc giai đoạn trước)
    private String awaitingPaymentPhaseName;
    private String awaitingPaymentInvoiceCode;   // Hóa đơn chủ ngựa cần trả để tiếp tục
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
        // Hóa đơn của giai đoạn. Giai đoạn sau chỉ thanh toán được khi giai đoạn trước đã hoàn thành lâm sàng.
        private Integer invoiceId;
        private String invoiceCode;
        private String invoiceStatus;
        private List<Item> items;
    }

    }

    

}
