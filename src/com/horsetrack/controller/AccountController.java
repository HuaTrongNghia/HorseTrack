package com.horsetrack.controller;

import com.horsetrack.dto.LoginDTO;
import com.horsetrack.dto.OwnerRegistrationDTO;
import com.horsetrack.entity.User;
import com.horsetrack.response.ApiResponse;
import com.horsetrack.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "*") // Mở CORS cho Frontend React
public class AccountController {

    @Autowired
    private AccountService accountService;

    // ==========================================
    // 1. API ĐĂNG KÝ TÀI KHOẢN CHỦ NGỰA
    // ==========================================
    @PostMapping("/register-owner")
    public ResponseEntity<ApiResponse<String>> registerOwner(
            @RequestBody OwnerRegistrationDTO dto) {

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

            // 4. Gọi AccountService để xử lý lưu vào Database (đã bao gồm kiểm tra trùng lặp và mã hóa BCrypt)
            accountService.createOwnerAccount(dto);

            System.out.println("Đã tạo tài khoản thành công cho Email: " + dto.getEmail());

            // 5. Phản hồi thành công
            return ResponseEntity.ok(
                    new ApiResponse<>(200, "Tạo tài khoản Chủ ngựa thành công!", null)
            );

        } catch (Exception e) {
            e.printStackTrace();
            // Bắt lỗi từ Service (ví dụ: trùng email, trùng username) và báo lỗi về cho Frontend
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }

    // ==========================================
    // 2. API ĐĂNG NHẬP
    // ==========================================
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<User>> login(@RequestBody LoginDTO loginDTO) {
        try {
            // Gọi Service để kiểm tra tài khoản và mật khẩu
            User loggedInUser = accountService.login(loginDTO);

            // BẮT BUỘC: Xóa mật khẩu đã mã hóa trước khi gửi về Frontend để bảo mật
            loggedInUser.setPasswordHash(null);

            // Trả về thông tin User nếu đăng nhập thành công
            return ResponseEntity.ok(
                    new ApiResponse<>(200, "Đăng nhập thành công!", loggedInUser)
            );
        } catch (Exception e) {
            // Báo lỗi sai tài khoản hoặc mật khẩu
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }
}