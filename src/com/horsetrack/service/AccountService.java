package com.horsetrack.service;

import com.horsetrack.dto.OwnerRegistrationDTO;
import com.horsetrack.entity.HorseOwnerProfile;
import com.horsetrack.entity.User;
import com.horsetrack.repository.HorseOwnerProfileRepository;
import com.horsetrack.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder; // Nhớ import thư viện này
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HorseOwnerProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder; // Gọi máy xay BCrypt ra đây

    @Transactional
    public void createOwnerAccount(OwnerRegistrationDTO dto) throws Exception {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new Exception("Email này đã được sử dụng. Vui lòng đăng nhập.");
        }
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new Exception("Tên đăng nhập này đã tồn tại.");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());

        // Băm nát mật khẩu trước khi lưu vào database
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));

        user.setRoleName("Horse Owner");

        User savedUser = userRepository.save(user);

        HorseOwnerProfile profile = new HorseOwnerProfile();
        profile.setUser(savedUser);
        profile.setBillingAddress(dto.getBillingAddress());
        profile.setTaxCode(dto.getTaxCode());

        profileRepository.save(profile);
    }
}