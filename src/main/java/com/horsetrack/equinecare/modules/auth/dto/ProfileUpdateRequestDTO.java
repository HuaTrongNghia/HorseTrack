package com.horsetrack.equinecare.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Người dùng sửa thông tin của chính mình (không đổi username, email, vai trò) */
@Data
public class ProfileUpdateRequestDTO {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ tên từ 2 đến 100 ký tự")
    private String fullName;

    // Bỏ trống để xóa số điện thoại
    @Pattern(regexp = "^$|^(0|\\+84)\\d{9,10}$", message = "Số điện thoại không hợp lệ (ví dụ 0912345678)")
    private String phone;

    // Mã chứng chỉ / mã nhân viên của HLV hoặc bác sĩ; bỏ trống = giữ nguyên
    @Pattern(regexp = "^\\s*$|^\\s*[A-Za-z0-9][A-Za-z0-9\\-_/.]{1,49}\\s*$", message = "Mã chứng chỉ 2-50 ký tự, chỉ gồm chữ, số và - _ / .")
    private String licenseNumber;

    // Số năm kinh nghiệm của HLV; bỏ trống = giữ nguyên
    @jakarta.validation.constraints.Min(value = 0, message = "Số năm kinh nghiệm không được âm")
    @jakarta.validation.constraints.Max(value = 60, message = "Số năm kinh nghiệm tối đa 60")
    private Integer experienceYears;

    // Phòng khám của bác sĩ thú y; bỏ trống = giữ nguyên
    @Size(max = 150, message = "Tên phòng khám tối đa 150 ký tự")
    private String clinicAffiliation;
}
