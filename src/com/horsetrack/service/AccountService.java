package com.horsetrack.service;

import com.horsetrack.dto.LoginDTO; // Đã bổ sung import cho LoginDTO
import com.horsetrack.dto.OwnerRegistrationDTO;
import com.horsetrack.entity.HorseOwnerProfile;
import com.horsetrack.entity.User;
import com.horsetrack.repository.HorseOwnerProfileRepository;
import com.horsetrack.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HorseOwnerProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    public User login(LoginDTO dto) throws Exception {
        // 1. Tìm tài khoản trong Database
        User user = userRepository.findByUsername(dto.getUsername())
                .orElseThrow(() -> new Exception("Sai tên đăng nhập hoặc mật khẩu!"));

        // 2. Dùng máy xay BCrypt để so sánh mật khẩu người dùng nhập với mật khẩu mã hóa trong DB
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new Exception("Sai tên đăng nhập hoặc mật khẩu!");
        }

        // 3. Kiểm tra xem tài khoản có bị vô hiệu hóa không
        if (!user.isActive()) {
            throw new Exception("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        // 4. Nếu mọi thứ hợp lệ, trả thông tin user về cho Frontend
        return user;
    }
}