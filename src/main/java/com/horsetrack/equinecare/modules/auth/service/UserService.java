package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.modules.auth.dto.UserLockRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserPageResponseDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserResponseDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserRoleChangeRequestDTO;

import java.util.List;

public interface UserService {

    UserPageResponseDTO getUsers(int page, int size, String search, String role, String status);

    List<UserResponseDTO> getAllUsers();

    UserResponseDTO getUserById(Integer userId);

    UserResponseDTO lockUser(Integer userId, UserLockRequestDTO request, String ipAddress);

    UserResponseDTO unlockUser(Integer userId, UserLockRequestDTO request, String ipAddress);

    UserResponseDTO changeRole(Integer userId, UserRoleChangeRequestDTO request, String ipAddress);
}
