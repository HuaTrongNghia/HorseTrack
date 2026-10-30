package com.horsetrack.equinecare.modules.horse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorseResponseDTO {

    private Integer horseId;
    private String horseName;
    private LocalDate dob;
    private String gender;
    private String breed;
    private String pedigree;
    private BigDecimal weightKg;
    private String healthStatus;
    private Boolean isLocked;
    private BigDecimal prizeSharePct;
    private Integer ownerId;
    private String ownerName;
    private String ownerPhone;         // lấy từ hồ sơ chủ ngựa
    private Integer stallId;
    private String stallCode;
    private String approvalStatus; // PENDING: chờ duyệt; APPROVED: đã duyệt và cấp chuồng; REJECTED: bị từ chối
    private LocalDateTime createdAt;
    private String horseCode;          // HT-0001 do DB sinh
    private java.util.List<com.horsetrack.equinecare.modules.medical.dto.InjuryDTO> injuries; // chỉ có ở GET /horses/{id}
    private String color;
    private BigDecimal heightCm;       // chiều cao đến vai (cm)
    // Hồ sơ đăng ký mới nhất
    private Integer registrationId;
    private String registrationCode;
    private String rejectReason;       // Có khi approvalStatus = REJECTED
    private Boolean allowResubmit;     // true: chủ ngựa được nộp lại

    private Integer assignedTrainerId;   // HLV được Club Manager phân công
    private String assignedTrainerName;

    // Thành tích thi đấu (giải đã nhập kết quả), chỉ có ở GET /horses/{id}
    private java.util.List<RaceResult> raceResults;
    private Integer raceCount;
    private Integer raceWins;
    private BigDecimal totalPrize;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RaceResult {
        private Integer proposalId;
        private String tournamentName;
        private LocalDate raceDate;
        private Integer rank;
        private BigDecimal finishTimeSeconds;
        private BigDecimal prizeAmount;
    }
}
