package com.horsetrack.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class TrainingPlanCreateRequestDTO {
    private String planName;
    private Integer horseId;
    private Integer trainerId;
    private LocalDate startDate;
    private LocalDate endDate;

    // Các thông số chuyên sâu khớp với bảng Training_Plans
    private Double targetDistanceM;
    private Double targetSpeedKmh;
    private String trackType;
    private String planStatus;

    // Danh sách các buổi tập lồng nhau
    private List<TrainingSessionCreateDTO> sessions;
}