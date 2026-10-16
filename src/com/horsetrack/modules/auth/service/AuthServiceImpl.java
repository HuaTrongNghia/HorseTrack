package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.modules.auth.dto.LoginDTO;
import com.horsetrack.equinecare.modules.auth.dto.OwnerRegisterRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.StaffCreateRequestDTO;
import com.horsetrack.equinecare.modules.auth.entity.*;
import com.horsetrack.equinecare.modules.auth.repository.*;
import com.horsetrack.equinecare.modules.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final HorseOwnerProfileRepository horseOwnerRepo;
    private final TrainerProfileRepository trainerRepo;
    private final VeterinarianProfileRepository vetRepo;
    private final GroomProfileRepository groomRepo;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerOwner(OwnerRegisterRequestDTO request) {
        if (userRepository.existsByUsername(request.getUsername().trim())) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại.");
        }
        if (userRepository.existsByEmail(request.getEmail().trim())) {
            throw new IllegalArgumentException("Email đã được sử dụng.");
        }

        User newUser = User.builder()
                .username(request.getUsername().trim())
                .fullName(request.getFullName().trim())
                .email(request.getEmail().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .roleName("Horse Owner")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(newUser);

        HorseOwnerProfile ownerProfile = HorseOwnerProfile.builder()
                .user(savedUser)
                .taxCode(request.getTaxCode())
                .billingAddress(request.getBillingAddress())
                .build();

        horseOwnerRepo.save(ownerProfile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createStaff(StaffCreateRequestDTO request) {
        if (userRepository.existsByUsername(request.getUsername().trim())) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại.");
        }
        if (userRepository.existsByEmail(request.getEmail().trim())) {
            throw new IllegalArgumentException("Email đã được sử dụng.");
        }

        User newUser = User.builder()
                .username(request.getUsername().trim())
                .fullName(request.getFullName().trim())
                .email(request.getEmail().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .roleName(request.getRoleName())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(newUser);

        switch (request.getRoleName()) {
            case "Trainer":
            case "Head Trainer":
                TrainerProfile trainerProfile = TrainerProfile.builder()
                .user(savedUser)
                .licenseNumber(request.getLicenseNumber() != null ? request.getLicenseNumber() : "N/A")
                .experienceYears(0)
                .build();
                trainerRepo.save(trainerProfile);
                break;
            case "Veterinarian":
                VeterinarianProfile vetProfile = VeterinarianProfile.builder()
                .user(savedUser)
                .vetLicenseNo(request.getLicenseNumber() != null ? request.getLicenseNumber() : "N/A")
                .clinicAffiliation(request.getSpecialization())
                .build();
                vetRepo.save(vetProfile);
                break;
            case "Groom":
                GroomProfile groomProfile = GroomProfile.builder()
                .user(savedUser)
                .assignedBarnZone(request.getSpecialization())
                .build();
                groomRepo.save(groomProfile);
                break;
            default:
                throw new IllegalArgumentException("Vai trò không hợp lệ: " + request.getRoleName());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String login(LoginDTO request) {
        // Bước 1: Lấy thông tin user từ DB dựa trên username từ LoginDTO
        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác"));

        // Kiểm tra xem tài khoản có đang hoạt động hay không
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new DisabledException("Tài khoản đã bị vô hiệu hóa hoặc chưa được kích hoạt");
        }

        // Bước 2: So khớp mật khẩu raw với password_hash từ Database bằng PasswordEncoder
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        // Bước 3, 4 & 5: Sinh JWT Token (chứa sub, userId, role) và trả về
        return jwtTokenProvider.generateToken(user);
    }
}
