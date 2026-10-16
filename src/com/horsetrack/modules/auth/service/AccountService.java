package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.modules.auth.dto.ChangePasswordRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserProfileResponseDTO;

public interface AccountService {
    UserProfileResponseDTO getMyProfile();
    void changePassword(ChangePasswordRequestDTO request);
}
