package com.horsetrack.equinecare.modules.medical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body chỉ gồm một đoạn văn bản: ghi chú tiến trình, kết quả điều trị hoặc lý do hủy. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProtocolTextRequestDTO {

    @NotBlank(message = "Nội dung (content) là bắt buộc")
    @Size(max = 1000, message = "Nội dung không được vượt quá 1000 ký tự")
    private String content;
}
