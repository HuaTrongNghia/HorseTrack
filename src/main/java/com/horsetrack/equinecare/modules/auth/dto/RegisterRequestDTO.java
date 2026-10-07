package com.horsetrack.equinecare.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO hứng dữ liệu từ giao diện "Đăng ký Tài khoản & Hồ sơ Chuyên Môn".
 */
@Data
public class RegisterRequestDTO {

    // --- SECTION 1: Thông tin định danh tài khoản (Bảng Users) ---
    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 4, max = 50, message = "Tên đăng nhập từ 4-50 ký tự")
    private String username;

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    private String email;

    private String phone;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    @NotBlank(message = "Xác nhận mật khẩu không được để trống")
    private String confirmPassword;

    private Boolean isActive = true; // Giá trị mặc định từ Toggle Switch

    // --- SECTION 2: Phân quyền vai trò ---
    @NotBlank(message = "Vai trò (Role) không được để trống")
    private String roleName;

    // --- SECTION 3: Dữ liệu Hồ sơ chuyên môn (Profiles) ---
    // Khai báo sẵn các trường cho các role khác nhau. Frontend chỉ gửi các trường tương ứng với Role được chọn.

    // Dành cho Horse Owner
    private String taxCode;
    private String billingAddress;

    // Dành cho Veterinarian
    private String vetLicenseNo;
    private String clinicAffiliation;

    // Dành cho Head Trainer
    private String trainerLicenseNumber;
    private Integer experienceYears;

    // Dành cho Club Manager
    private String department;

    // Dành cho Groom
    private String assignedBarnZone;
    private String shiftType;
}