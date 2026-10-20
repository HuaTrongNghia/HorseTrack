package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.modules.auth.dto.LoginDTO;
import com.horsetrack.equinecare.modules.auth.dto.LoginResponseDTO;
import com.horsetrack.equinecare.modules.auth.dto.OwnerRegisterRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.RefreshTokenRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.StaffCreateRequestDTO;

public interface AuthService {
    void registerOwner(OwnerRegisterRequestDTO request);
    void createStaff(StaffCreateRequestDTO request);
    LoginResponseDTO login(LoginDTO request);
    LoginResponseDTO refreshToken(RefreshTokenRequestDTO request);
    void logout(RefreshTokenRequestDTO request, String accessToken);
    void forgotPassword(com.horsetrack.equinecare.modules.auth.dto.ForgotPasswordRequestDTO request);
    void resetPassword(com.horsetrack.equinecare.modules.auth.dto.ResetPasswordRequestDTO request);
}
