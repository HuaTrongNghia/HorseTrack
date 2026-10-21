package com.horsetrack.equinecare.modules.nutrition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DietChangeRequestCreateDTO {

    @NotNull(message = "horseId không được để trống")
    private Integer horseId;

    private Integer itemId;

    @NotBlank(message = "Tên thức ăn / phụ gia (requestDetail hoặc item) không được để trống")
    @Size(max = 255, message = "Chi tiết yêu cầu không vượt quá 255 ký tự")
    private String item;

    @NotBlank(message = "Lý do (reason) không được để trống")
    @Size(min = 10, max = 500, message = "Lý do phải có ít nhất 10 ký tự và không vượt quá 500 ký tự")
    private String reason;
}
