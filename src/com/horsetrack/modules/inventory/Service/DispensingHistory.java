package com.horsetrack.equinecare.modules.inventory.service;

import com.horsetrack.equinecare.modules.inventory.entity.StockHold;
import com.horsetrack.equinecare.modules.inventory.entity.StockTransaction;
import com.horsetrack.equinecare.modules.inventory.repository.StockHoldRepository;
import com.horsetrack.equinecare.modules.inventory.repository.StockTransactionRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lịch sử cấp phát thuốc theo phác đồ (I1): đọc các dòng ISSUE của sổ kho có ref PROTOCOL.
 * Mỗi lần cấp ghi mã giữ hàng vào ghi chú ("[giữ hàng #12]") để gắn dòng xuất kho với dòng stockAnswer;
 * dữ liệu cấp trước khi có mã này được gắn theo vật tư.
 */
@Component
@RequiredArgsConstructor
public class DispensingHistory {

    private static final Pattern HOLD_TAG = Pattern.compile("\\[giữ hàng #(\\d+)]");
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StockTransactionRepository transactionRepository;
    private final StockHoldRepository holdRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Record {
        private String txCode;
        private Integer holdId;
        private Integer itemId;
        private String itemName;
        private String unit;
        private Double quantity;
        private String lotNo;
        private LocalDate expiryDate;
        private LocalDateTime issuedAt;
        private String issuedByName;
        private String note;

        public String lotLabel() {
            String qty = StockLedgerService.fmt(quantity);
            return (lotNo != null ? lotNo : "Không ghi lô") + " x " + qty
                    + (expiryDate != null ? " (HSD " + expiryDate.format(VN_DATE) + ")" : "");
        }
    }

    public static String holdTag(Integer holdId) {
        return "[giữ hàng #" + holdId + "]";
    }

    public List<Record> records(Integer protocolId) {
        List<StockTransaction> txs = transactionRepository.findByRefTypeAndRefIdAndTxTypeOrderByPerformedAtAscTransactionIdAsc("PROTOCOL", protocolId, "ISSUE");
        // Dữ liệu cũ không có mã giữ hàng: gắn với dòng ISSUED cùng vật tư (dòng đầu tiên)
        Map<Integer, Integer> fallbackHold = new HashMap<>();
        for (StockHold h : holdRepository.findByProtocolIdAndStatusIn(protocolId, List.of("ISSUED"))) {
            fallbackHold.putIfAbsent(h.getItem().getItemId(), h.getHoldId());
        }
        List<Record> result = new ArrayList<>();
        for (StockTransaction t : txs) {
            Integer holdId = null;
            if (t.getNote() != null) {
                Matcher m = HOLD_TAG.matcher(t.getNote());
                if (m.find()) {
                    holdId = Integer.valueOf(m.group(1));
                }
            }
            if (holdId == null) {
                holdId = fallbackHold.get(t.getItem().getItemId());
            }
            result.add(Record.builder()
                    .txCode(t.getTxCode())
                    .holdId(holdId)
                    .itemId(t.getItem().getItemId())
                    .itemName(t.getItem().getItemName())
                    .unit(t.getItem().getUnit())
                    .quantity(t.getQuantity())
                    .lotNo(t.getLot() != null ? t.getLot().getLotNo() : null)
                    .expiryDate(t.getLot() != null ? t.getLot().getExpiryDate() : null)
                    .issuedAt(t.getPerformedAt())
                    .issuedByName(t.getPerformedBy() != null ? t.getPerformedBy().getFullName() : null)
                    .note(t.getNote() != null ? HOLD_TAG.matcher(t.getNote()).replaceAll("").trim() : null)
                    .build());
        }
        return result;
    }

    /** Dòng cấp phát gom theo mã giữ hàng */
    public Map<Integer, List<Record>> byHold(Integer protocolId) {
        Map<Integer, List<Record>> map = new LinkedHashMap<>();
        for (Record r : records(protocolId)) {
            if (r.getHoldId() != null) {
                map.computeIfAbsent(r.getHoldId(), k -> new ArrayList<>()).add(r);
            }
        }
        return map;
    }
}
