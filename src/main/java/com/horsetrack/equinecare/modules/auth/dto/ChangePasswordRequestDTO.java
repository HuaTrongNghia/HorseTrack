package com.horsetrack.equinecare.modules.auth.dto;

import com.horsetrack.equinecare.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequestDTO {

    @NotBlank(message = "Old password is required")
    private String oldPassword;

    @NotBlank(message = "Mật khẩu mới là bắt buộc")
    @StrongPassword
    private String newPassword;

    @NotBlank(message = "Confirm new password is required")
    private String confirmNewPassword;

    // Mã OTP gửi tới email qua POST /api/v1/account/change-password/otp
    @NotBlank(message = "Mã OTP là bắt buộc")
    @Pattern(regexp = "^\\d{6}$", message = "Mã OTP gồm đúng 6 chữ số")
    private String otp;
}
