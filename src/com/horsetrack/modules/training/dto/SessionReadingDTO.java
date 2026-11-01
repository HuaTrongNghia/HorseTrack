package com.horsetrack.equinecare.modules.training.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionReadingDTO {
    private Long readingId;
    /** Giờ đo dạng HH:mm */
    private String time;
    private LocalDateTime recordedAt;
    private Integer heartRate;
    private BigDecimal speedKmh;
}
