package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.modules.auth.dto.RegisterRequestDTO;
import com.horsetrack.equinecare.modules.auth.entity.User;

public interface AuthService {
    /**
     * Xử lý đăng ký tài khoản từ form tiếp nhận của Frontend.
     * @param request Dữ liệu nhập từ giao diện
     * @return User sau khi tạo thành công
     */
    User register(RegisterRequestDTO request);
}