package com.horsetrack.equinecare.modules.medical.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Bác sĩ tạo lịch tiêm phòng / tẩy giun cho nhiều ngựa */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateScheduleRequestDTO {

    @NotEmpty(message = "Vui lòng chọn ít nhất một ngựa!")
    @Size(max = 100, message = "Tối đa 100 ngựa mỗi lần")
    private List<Integer> horseIds;

    // VACCINE (mặc định) hoặc DEWORM
    @Pattern(regexp = "(?i)^(VACCINE|DEWORM)?$", message = "kind chỉ nhận VACCINE hoặc DEWORM")
    private String kind;

    // Tên vaccine / thuốc tẩy giun
    @NotBlank(message = "Phải nhập tên vaccine / thuốc (medicineName)")
    @Size(min = 3, max = 100, message = "Tên vaccine / thuốc từ 3 đến 100 ký tự")
    private String medicineName;

    // Ngày dự kiến: yyyy-MM-dd hoặc dd/MM/yyyy, không ở quá khứ
    @NotBlank(message = "Phải nhập ngày dự kiến (scheduledDate)")
    private String scheduledDate;

    // Chu kỳ nhắc lại (tháng): vaccine 1-24, tẩy giun 3-6; bỏ trống = không nhắc lại
    private Integer cycleMonths;

    // Vật tư trong kho (tùy chọn): phải thuộc nhóm Thuốc / Vaccine
    private Integer itemId;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String notes;
}
