package com.horsetrack.equinecare.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body chỉ gồm lý do: dùng cho hủy đơn mua, từ chối yêu cầu vật tư... */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReasonRequestDTO {

    @NotBlank(message = "Lý do (reason) là bắt buộc")
    @Size(max = 200, message = "Lý do không được vượt quá 200 ký tự")
    private String reason;
}
