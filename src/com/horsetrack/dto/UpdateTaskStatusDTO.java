package com.horsetrack.dto;

import lombok.Data;

@Data
public class UpdateTaskStatusDTO {
    private Long taskId;
    private String status; // "COMPLETED" hoặc "CANCELLED"
    private String notes;  // Bắt buộc nếu status là CANCELLED
}