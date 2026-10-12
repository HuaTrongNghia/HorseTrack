package com.horsetrack.equinecare.modules.auth.service.impl;

import com.horsetrack.equinecare.modules.auth.dto.ChangePasswordRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserProfileResponseDTO;
import com.horsetrack.equinecare.modules.auth.entity.TrainerProfile;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.entity.VeterinarianProfile;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.auth.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponseDTO getMyProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfileResponseDTO dto = UserProfileResponseDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .roleName(user.getRoleName())
                .isActive(user.getIsActive())
                .build();

        if ("TRAINER".equalsIgnoreCase(user.getRoleName()) || "HEAD_TRAINER".equalsIgnoreCase(user.getRoleName())) {
            TrainerProfile trainer = user.getTrainerProfile();
            if (trainer != null) {
                dto.setLicenseNumber(trainer.getLicenseNumber());
                dto.setExperienceYears(trainer.getExperienceYears());
            }
        } else if ("VETERINARIAN".equalsIgnoreCase(user.getRoleName())) {
            VeterinarianProfile vet = user.getVeterinarianProfile();
            if (vet != null) {
                dto.setLicenseNumber(vet.getVetLicenseNo());
                dto.setClinicAffiliation(vet.getClinicAffiliation());
            }
        }

        return dto;
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequestDTO request) {
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new RuntimeException("New password and confirm password do not match");
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Old password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
