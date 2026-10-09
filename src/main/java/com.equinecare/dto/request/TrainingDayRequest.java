package com.equinecare.dto.request;

import lombok.Data;
import java.time.LocalDate;

@Data
public class TrainingDayRequest {
    private LocalDate trainingDate;
    private Integer groomId;
    private Integer durationMinutes;
}