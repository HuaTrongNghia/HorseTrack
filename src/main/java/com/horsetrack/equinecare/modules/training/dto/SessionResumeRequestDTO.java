package com.horsetrack.equinecare.modules.training.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** HLV điều chỉnh và cho buổi tập đang tạm dừng tập tiếp */
@Data
public class SessionResumeRequestDTO {

    @Size(max = 50, message = "Cường độ tối đa 50 ký tự")
    private String intensity;

    @Min(value = 1, message = "Cự ly phải lớn hơn 0") @Max(value = 20000, message = "Cự ly tối đa 20000 m")
    private Integer distanceM;

    // Ngưỡng nhịp tim mới cho phần còn lại của buổi tập; bỏ trống = giữ ngưỡng cũ
    @Min(value = 60, message = "Ngưỡng nhịp tim tối thiểu 60 bpm") @Max(value = 300, message = "Ngưỡng nhịp tim tối đa 300 bpm")
    private Integer maxHeartRate;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
