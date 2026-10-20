package com.horsetrack.equinecare.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCountDTO {
    private Integer countId;
    private String countCode;
    private String countMonth;
    private String status;
    private String countedByName;
    private LocalDateTime countedAt;
    private String note;
    private Integer totalLines;
    private Integer linesWithDifference;
    private List<Line> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private Integer itemId;
        private String itemCode;
        private String itemName;
        private String unit;
        private Double systemQty;
        private Double countedQty;
        private Double difference; // Dương: thừa, âm: thiếu
        private String reason;
    }
}
