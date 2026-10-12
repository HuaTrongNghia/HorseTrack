package com.horsetrack.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class TrainingPlanDTO {
    private String planName;      // Tên giáo án
    private String objective;     // Mục tiêu
    private String horseId;       // Mã ngựa
    private LocalDate startDate;  // Từ ngày
    private LocalDate endDate;    // Đến ngày
}