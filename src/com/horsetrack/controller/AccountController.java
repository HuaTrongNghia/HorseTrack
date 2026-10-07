package com.horsetrack.controller;

import com.horsetrack.dto.OwnerRegistrationDTO;
import com.horsetrack.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "*") // Mở CORS cho Frontend React
public class AccountController {

    @PostMapping("/register-owner")
    public ResponseEntity<ApiResponse<String>> registerOwner(
            @RequestBody OwnerRegistrationDTO dto) { // Dùng @RequestBody để nhận JSON từ React

        try {
            // 1. Kiểm tra bắt buộc đồng ý điều khoản
            if (!dto.isAgreeToTerms()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(400, "Vui lòng đồng ý với Điều khoản sử dụng và Chính sách bảo mật.", null));
            }

            // 2. Kiểm tra mật khẩu có khớp nhau không
            if (dto.getPassword() == null || !dto.getPassword().equals(dto.getConfirmPassword())) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(400, "Mật khẩu xác nhận không khớp.", null));
            }

            // 3. Kiểm tra độ dài mật khẩu (theo UI là tối thiểu 8 ký tự)
            if (dto.getPassword().length() < 8) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(400, "Mật khẩu phải có tối thiểu 8 ký tự.", null));
            }

            // TODO: Bổ sung logic kiểm tra Email đã tồn tại trong DB chưa
            // TODO: Mã hóa mật khẩu (dùng BCrypt) trước khi lưu
            // ownerService.createOwnerAccount(com.horsetrack.dto);

            System.out.println("Đã tiếp nhận yêu cầu tạo tài khoản cho Email: " + dto.getEmail());

            // 4. Phản hồi thành công
            return ResponseEntity.ok(
                    new ApiResponse<>(200, "Tạo tài khoản Chủ ngựa thành công!", null)
            );

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse<>(500, "Lỗi hệ thống: " + e.getMessage(), null));
        }
    }
}