package com.equinecare.dto.request;

import lombok.Data;
import java.time.LocalDate;

@Data
public class TrainingDayCreateRequest {
    private Integer groomId;
    private Integer trainerId;
    private LocalDate trainingDate;
    private Integer durationMinutes;
}