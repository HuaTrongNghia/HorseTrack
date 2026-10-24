package com.horsetrack.equinecare.modules.medical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Một mũi tiêm phòng / tẩy giun. Giữ các trường cũ (recordCode, typeName, expectedDate dd/MM/yyyy...) cho FE */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaccinationRecordDTO {
    private Integer id;
    private String recordCode;        // VC-0001
    private Integer horseId;
    private String horseName;
    private String kind;              // VACCINE / DEWORM
    private String typeName;          // tên vaccine / thuốc tẩy giun
    private String category;          // "Vaccine" / "Tẩy giun"
    private LocalDate dueDate;
    private String expectedDate;      // dd/MM/yyyy
    private String countdown;         // "còn 3 ngày", "hôm nay", "quá hạn 2 ngày"
    private Integer cycleMonths;
    private String statusCode;        // OVERDUE, DUE_SOON (trong 7 ngày), SCHEDULED, POSTPONED, DONE, SKIPPED
    private String status;            // nhãn tiếng Việt
    private LocalDateTime doneAt;
    private String doneByName;
    private String lotNo;
    private Integer itemId;
    private String itemName;
    private String note;
}
