package com.horsetrack.equinecare.modules.tournament.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RaceResultDTO {

    @NotNull(message = "Thứ hạng là bắt buộc")
    @Min(value = 1, message = "Thứ hạng từ 1 trở lên") @Max(value = 100, message = "Thứ hạng tối đa 100")
    private Integer rank;

    // Thời gian về đích: số giây (72.45) hoặc "m:ss.xx" (1:12.45)
    private String finishTime;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
