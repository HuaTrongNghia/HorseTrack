package com.horsetrack.equinecare.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLockRequestDTO {

    @NotBlank(message = "Lý do không được để trống")
    @Size(min = 10, message = "Lý do phải có ít nhất 10 ký tự")
    private String reason;
}
