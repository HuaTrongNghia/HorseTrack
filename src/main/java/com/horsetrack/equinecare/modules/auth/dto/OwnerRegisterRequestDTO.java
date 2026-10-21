package com.horsetrack.equinecare.modules.auth.dto;

import com.horsetrack.equinecare.common.validation.StrongPassword;
import com.horsetrack.equinecare.common.validation.ValidUsername;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerRegisterRequestDTO {

    @NotBlank(message = "Tên đăng nhập là bắt buộc")
    @ValidUsername
    private String username;

    @NotBlank(message = "Mật khẩu là bắt buộc")
    @StrongPassword
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String phone;
}
