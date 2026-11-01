package com.horsetrack.equinecare.modules.training.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionEvaluateRequestDTO {

    @NotNull(message = "Score is required")
    @Min(value = 1, message = "Điểm phong độ phải từ 1 đến 10")
    @Max(value = 10, message = "Điểm phong độ phải từ 1 đến 10")
    private Integer score;

    private String comment;
}
