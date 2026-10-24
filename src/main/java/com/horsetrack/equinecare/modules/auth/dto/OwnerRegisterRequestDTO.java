package com.horsetrack.equinecare.modules.auth.dto;

import com.horsetrack.equinecare.common.validation.StrongPassword;
import com.horsetrack.equinecare.common.validation.ValidUsername;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chủ ngựa tự đăng ký, 2 bước trên cùng API:
 * gửi không có otp -> hệ thống kiểm tra trùng và gửi mã OTP tới email; gửi lại cùng thông tin kèm otp -> tạo tài khoản.
 */
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

    @NotBlank(message = "Họ và tên là bắt buộc")
    @Size(min = 2, max = 100, message = "Họ và tên từ 2 đến 100 ký tự")
    private String fullName;

    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @NotBlank(message = "Số điện thoại là bắt buộc")
    @Pattern(regexp = "^\\s*(0|\\+84)\\d{9,10}\\s*$", message = "Số điện thoại không hợp lệ (ví dụ 0912345678)")
    private String phone;

    // Mã OTP gửi tới email ở bước 1; bỏ trống = yêu cầu (gửi lại) mã
    @Pattern(regexp = "^\\s*$|^\\s*\\d{6}\\s*$", message = "Mã OTP gồm 6 chữ số")
    private String otp;
}
