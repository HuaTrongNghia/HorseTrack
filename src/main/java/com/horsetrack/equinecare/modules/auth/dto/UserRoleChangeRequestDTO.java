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
public class UserRoleChangeRequestDTO {

    @NotBlank(message = "Vai trò mới không được để trống")
    private String roleName;

    @NotBlank(message = "Lý do đổi vai trò không được để trống")
    @Size(min = 10, message = "Lý do phải có ít nhất 10 ký tự")
    private String reason;
}
