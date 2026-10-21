package com.horsetrack.equinecare.modules.medical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreRaceExamDTO {
    // Thông tin Đề xuất
    private String proposalCode;
    private String proposalDate;

    // Thông tin Ngựa
    private String horseName;
    private String ownerName;

    // Thông tin Giải đấu
    private String tournamentName;
    private String raceDate;
    private String distance;
    private String location;

    // Đề xuất bởi
    private String trainerName;

    // Tình trạng sức khỏe hiện tại
    private String healthStatus;
    private String warningTag;

    // Dành cho tab Đã có kết quả
    private String tabStatus;
    private String resultStatus;
    private String vetNotes;
    private String nextStepStatus;
}
