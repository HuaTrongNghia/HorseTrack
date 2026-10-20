package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.modules.auth.dto.ChangePasswordRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserProfileResponseDTO;

public interface AccountService {
    UserProfileResponseDTO getMyProfile();
    /** Gửi OTP đổi mật khẩu tới email của người đang đăng nhập; trả về email đã che bớt. */
    String requestChangePasswordOtp();
    void changePassword(ChangePasswordRequestDTO request);
}
