package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.modules.auth.dto.OwnerRegisterRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.StaffCreateRequestDTO;

public interface AuthService {
    void registerOwner(OwnerRegisterRequestDTO request);
    void createStaff(StaffCreateRequestDTO request);
}