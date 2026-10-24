package com.horsetrack.equinecare.modules.medical.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** Bác sĩ thêm thuốc vào phác đồ đang điều trị */
@Data
public class ProtocolItemAddRequestDTO {

    @NotNull(message = "Phải chọn giai đoạn (phaseNo)")
    @Min(value = 1, message = "phaseNo từ 1 trở lên")
    private Integer phaseNo;

    @NotNull(message = "Phải chọn thuốc (itemId)")
    private Integer itemId;

    @NotNull(message = "Phải nhập số lượng")
    @DecimalMin(value = "0.01", message = "Số lượng phải lớn hơn 0")
    @DecimalMax(value = "100000", message = "Số lượng không hợp lệ")
    private Double quantity;

    @Size(max = 100, message = "Liều dùng tối đa 100 ký tự")
    private String dosage;

    @NotBlank(message = "Phải ghi lý do bổ sung thuốc")
    @Size(min = 5, max = 500, message = "Lý do từ 5 đến 500 ký tự")
    private String reason;
}
