package com.horsetrack.equinecare.modules.auth.dto;

import com.horsetrack.equinecare.common.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequestDTO {

    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Mã OTP là bắt buộc")
    @Pattern(regexp = "^\\d{6}$", message = "Mã OTP gồm đúng 6 chữ số")
    private String otp;

    @NotBlank(message = "Mật khẩu mới là bắt buộc")
    @StrongPassword
    private String newPassword;

    @NotBlank(message = "Xác nhận mật khẩu mới là bắt buộc")
    private String confirmNewPassword;
}
