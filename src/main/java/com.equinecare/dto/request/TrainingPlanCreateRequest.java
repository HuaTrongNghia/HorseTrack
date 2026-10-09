package com.equinecare.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class TrainingPlanCreateRequest {
    private Integer horseId;
    private Integer trainerId;
    private String planName;
    private BigDecimal targetDistanceM;
    private BigDecimal targetSpeedKmh;
    private String trackType;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<TrainingDayRequest> trainingDays;
}