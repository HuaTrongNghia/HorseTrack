package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.modules.auth.dto.LoginDTO;
import com.horsetrack.equinecare.modules.auth.dto.LoginResponseDTO;
import com.horsetrack.equinecare.modules.auth.dto.OwnerRegisterRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.RefreshTokenRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.StaffCreateRequestDTO;

public interface AuthService {
    /** Bước 1 đăng ký chủ ngựa: kiểm tra trùng, kiểm tra email nhận được thư, gửi OTP. Trả về email đã che bớt. */
    String requestOwnerRegistrationOtp(OwnerRegisterRequestDTO request);
    /** Bước 2: xác thực OTP rồi tạo tài khoản. */
    void registerOwner(OwnerRegisterRequestDTO request);
    /** Tạo tài khoản nhân viên (chưa xác nhận); trả về mật khẩu / mã xác nhận gốc để gửi email */
    StaffAccountService.CreatedStaff createStaff(StaffCreateRequestDTO request);
    LoginResponseDTO login(LoginDTO request);
    LoginResponseDTO refreshToken(RefreshTokenRequestDTO request);
    void logout(RefreshTokenRequestDTO request, String accessToken);
    void forgotPassword(com.horsetrack.equinecare.modules.auth.dto.ForgotPasswordRequestDTO request);
    void resetPassword(com.horsetrack.equinecare.modules.auth.dto.ResetPasswordRequestDTO request);
}