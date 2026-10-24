package com.horsetrack.equinecare.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {

    // Tên đăng nhập, email hoặc số điện thoại
    @NotBlank(message = "Tên đăng nhập / email / số điện thoại không được để trống")
    private String username;

    @NotBlank(message = "Password không được để trống")
    private String password;
}