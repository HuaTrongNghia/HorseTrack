package com.horsetrack.equinecare.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Quản lý CLB sửa email của tài khoản nhân viên chưa xác nhận */
@Data
public class ChangeStaffEmailDTO {

    @NotBlank(message = "Email mới là bắt buộc")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;
}
