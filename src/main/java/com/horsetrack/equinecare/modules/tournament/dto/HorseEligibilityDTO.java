package com.horsetrack.equinecare.modules.tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Kết quả lọc điều kiện một con ngựa cho một giải, kèm phong độ để xếp hạng */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorseEligibilityDTO {
    private Integer horseId;
    private String horseCode;
    private String horseName;
    private String breed;
    private Integer ageYears;
    private BigDecimal weightKg;
    private Boolean eligible;
    /** Từng tiêu chí: đạt / không đạt và lý do */
    private List<Check> checks;
    /** Lý do không phù hợp (chỉ các tiêu chí không đạt) */
    private List<String> reasons;
    /** Phong độ 0-100 (ưu tiên chỉ số đo được; điểm HLV chỉ là một phần); null = chưa có dữ liệu tập */
    private BigDecimal formScore;
    private String formNote;
    /** Thứ hạng phong độ trong số ngựa của chủ (1 = tốt nhất) */
    private Integer formRank;
    /** Ngựa hệ thống gợi ý (phù hợp và phong độ cao nhất) */
    private Boolean recommended;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Check {
        /** APPROVED, HAS_TRAINER, AGE, WEIGHT, BREED, NOT_LOCKED, NOT_IN_TREATMENT, NO_DATE_CONFLICT */
        private String code;
        private String label;
        private Boolean passed;
        private String detail;
    }
}
