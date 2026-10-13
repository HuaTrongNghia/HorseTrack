package com.horsetrack.dto;

import lombok.Data;

@Data
public class VaccinationRecordDTO {
    private String recordCode;     // VD: "VC-036"
    private String horseName;      // VD: "Bolt"
    private String typeName;       // VD: "Uốn ván"
    private String category;       // "Vaccine" hoặc "Tẩy giun"
    private String expectedDate;   // VD: "04/10/2026"
    private String countdown;      // VD: "còn 1 ngày"
    private Integer cycleMonths;   // Chu kỳ (VD: 12 tháng)
    private String status;         // "Sắp đến hạn", "Hoãn", "Đã thực hiện"

    // Các trường phụ cho tab "Đã thực hiện"
    private String executionDate;  // VD: "20/09/2026"
    private String batchNumber;    // Lô thuốc (VD: "Lô VX-CUM-2608")
    private String vetName;        // Người thực hiện (VD: "Lê Thị Hương")
}