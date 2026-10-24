package com.horsetrack.equinecare.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {

    // Email, số điện thoại hoặc tên đăng nhập; vẫn nhận khóa "username" để FE cũ không phải đổi ngay
    @Schema(description = "Email, số điện thoại hoặc tên đăng nhập", example = "owner01@gmail.com")
    @JsonAlias({"username", "email", "phone"})
    @NotBlank(message = "Email / số điện thoại / tên đăng nhập không được để trống")
    private String identifier;

    @Schema(description = "Mật khẩu", example = "Horse@12345")
    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;
}
