package com.horsetrack.equinecare.modules.auth.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.auth.dto.UserLockRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserPageResponseDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserResponseDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserRoleChangeRequestDTO;
import com.horsetrack.equinecare.modules.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "User Management", description = "Các API quản lý người dùng, phân quyền RBAC và kiểm soát tài khoản (BR_06, BR_10)")
public class UserController {

    private final UserService userService;

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Lấy danh sách người dùng phân trang & tìm kiếm",
               description = "Tìm kiếm theo từ khóa (tên, username, email), lọc theo vai trò (role) và trạng thái (ACTIVE/LOCKED).")
    public ResponseEntity<ApiResponse<UserPageResponseDTO>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        UserPageResponseDTO response = userService.getUsers(page, size, search, role, status);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách người dùng thành công", response));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Lấy toàn bộ danh sách người dùng (không phân trang)",
               description = "Dành cho FE lấy danh sách đầy đủ để hiển thị bảng dữ liệu tổng quát.")
    public ResponseEntity<ApiResponse<List<UserResponseDTO>>> getAllUsers() {
        List<UserResponseDTO> response = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.ok("Lấy toàn bộ danh sách người dùng thành công", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Xem chi tiết một người dùng theo ID", description = "Lấy thông tin tài khoản và hồ sơ nghiệp vụ.")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getUserById(@PathVariable Integer id) {
        UserResponseDTO response = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết người dùng thành công", response));
    }

    @PutMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Khóa tài khoản người dùng (BR_10)",
               description = "Khóa tài khoản kèm lý do (tối thiểu 10 ký tự), tự động thu hồi phiên đăng nhập và ghi Audit Log.")
    public ResponseEntity<ApiResponse<UserResponseDTO>> lockUser(
            @PathVariable Integer id,
            @Valid @RequestBody UserLockRequestDTO request,
            HttpServletRequest servletRequest) {
        UserResponseDTO response = userService.lockUser(id, request, getClientIp(servletRequest));
        return ResponseEntity.ok(ApiResponse.ok("Đã khóa tài khoản thành công", response));
    }

    @PutMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Mở khóa tài khoản người dùng (BR_10)",
               description = "Mở khóa tài khoản kèm lý do (tối thiểu 10 ký tự) và ghi Audit Log.")
    public ResponseEntity<ApiResponse<UserResponseDTO>> unlockUser(
            @PathVariable Integer id,
            @Valid @RequestBody UserLockRequestDTO request,
            HttpServletRequest servletRequest) {
        UserResponseDTO response = userService.unlockUser(id, request, getClientIp(servletRequest));
        return ResponseEntity.ok(ApiResponse.ok("Đã mở khóa tài khoản thành công", response));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasAuthority('Club Manager')")
    @Operation(summary = "Đổi vai trò người dùng (BR_06 & BR_10)",
               description = "Đổi vai trò người dùng, kiểm tra xung đột lợi ích theo BR_06 (người đang sở hữu ngựa không thể làm nhân viên CLB) và ghi Audit Log.")
    public ResponseEntity<ApiResponse<UserResponseDTO>> changeRole(
            @PathVariable Integer id,
            @Valid @RequestBody UserRoleChangeRequestDTO request,
            HttpServletRequest servletRequest) {
        UserResponseDTO response = userService.changeRole(id, request, getClientIp(servletRequest));
        return ResponseEntity.ok(ApiResponse.ok("Đổi vai trò người dùng thành công", response));
    }
}
