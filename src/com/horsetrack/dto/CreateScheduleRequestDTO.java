package com.horsetrack.dto;

import lombok.Data;
import java.util.List;

@Data
public class CreateScheduleRequestDTO {
    private String category;       // "Vaccine" hoặc "Deworming"
    private String medicineName;   // VD: "Tẩy giun định kỳ"
    private String expectedDate;   // VD: "10/10/2026"
    private Integer cycleMonths;   // VD: 3
    private List<Integer> horseIds; // Danh sách ID ngựa được chọn (Checkboxes)
    private String notes;          // Ghi chú
}