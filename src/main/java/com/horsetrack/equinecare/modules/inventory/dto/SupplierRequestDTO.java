package com.horsetrack.equinecare.modules.inventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierRequestDTO {

    @NotBlank(message = "Tên nhà cung cấp (supplierName) là bắt buộc")
    @Size(max = 150, message = "Tên nhà cung cấp không được vượt quá 150 ký tự")
    private String supplierName;

    @Pattern(regexp = "^[0-9+\\-\\s]{8,20}$", message = "Số điện thoại chỉ gồm 8-20 ký tự số, dấu +, - hoặc khoảng trắng")
    private String contactPhone;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String contactEmail;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    // Chỉ dùng khi cập nhật; khi thêm mới luôn là true
    private Boolean isActive;
}
