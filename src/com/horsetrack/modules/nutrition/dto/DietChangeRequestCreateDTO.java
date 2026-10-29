
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
    @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;
}
