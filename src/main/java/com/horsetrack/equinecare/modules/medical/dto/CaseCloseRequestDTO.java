package com.horsetrack.equinecare.modules.medical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaseCloseRequestDTO {

    @NotBlank(message = "Kết luận (outcome) là bắt buộc khi đóng ca")
    @Size(max = 1000, message = "Kết luận không được vượt quá 1000 ký tự")
    private String outcome;
}
