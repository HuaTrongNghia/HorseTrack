package com.horsetrack.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class SubmitExamResultDTO {

    @NotNull(message = "Phải chọn kết quả ĐẠT hoặc KHÔNG ĐẠT")
    private Boolean isPassed; // true = ĐẠT, false = KHÔNG ĐẠT

    @NotBlank(message = "Vui lòng nhập nhận xét chuyên môn")
    private String vetNotes;  // Lời phê của bác sĩ
}