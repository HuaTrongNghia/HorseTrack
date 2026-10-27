package com.horsetrack.equinecare.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.horsetrack.equinecare.common.validation.StrongPassword;
import com.horsetrack.equinecare.common.validation.ValidUsername;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffCreateRequestDTO {

    @NotBlank(message = "Tên đăng nhập là bắt buộc")
    @ValidUsername
    private String username;

    // Gửi trong email cùng tên đăng nhập và nút xác nhận tài khoản
    @NotBlank(message = "Mật khẩu là bắt buộc")
    @StrongPassword
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String phone;

    @NotBlank(message = "Role name is required")
    @Pattern(regexp = "^(Trainer|Veterinarian|Groom|Warehouse Manager)$", message = "Role must be Trainer, Veterinarian, Groom, or Warehouse Manager")
    private String roleName;

    // Optional fields for specific roles
    private String licenseNumber;

    /** Groom: khu chuồng phụ trách (cũng nhận tên barnZone). Bác sĩ: phòng khám. */
    @JsonAlias("barnZone")
    private String specialization;
}
