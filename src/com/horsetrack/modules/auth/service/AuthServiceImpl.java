package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.modules.auth.dto.ForgotPasswordRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.ResetPasswordRequestDTO;
import com.horsetrack.equinecare.modules.auth.security.OtpService;
import com.horsetrack.equinecare.modules.notification.exception.EmailSendException;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import com.horsetrack.equinecare.modules.auth.dto.LoginDTO;
import com.horsetrack.equinecare.modules.auth.dto.LoginResponseDTO;
import com.horsetrack.equinecare.modules.auth.dto.OwnerRegisterRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.RefreshTokenRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.StaffCreateRequestDTO;
import com.horsetrack.equinecare.modules.auth.entity.*;
import com.horsetrack.equinecare.modules.auth.repository.*;
import com.horsetrack.equinecare.modules.auth.security.JwtTokenProvider;
import com.horsetrack.equinecare.modules.auth.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final EmailService emailService;

    // ==========================================
    // QUÊN MẬT KHẨU (OTP qua email)
    // ==========================================

    @Override
    public void forgotPassword(ForgotPasswordRequestDTO request) {
        String email = request.getEmail().trim();
        // Áp thời gian chờ cho mọi email (kể cả không tồn tại) để phản hồi giống nhau, không lộ email nào đã đăng ký
        otpService.checkAndMarkCooldown(email, OtpService.Purpose.RESET_PASSWORD);

        userRepository.findByEmail(email)
                .filter(user -> !Boolean.FALSE.equals(user.getIsActive()))
                .ifPresent(user -> {
                    String otp = otpService.issue(email, OtpService.Purpose.RESET_PASSWORD);
                    try {
                        emailService.sendOtp(user.getEmail(), user.getFullName(), otp, OtpService.Purpose.RESET_PASSWORD.getLabel());
                    } catch (EmailSendException ex) {
                        // Gửi không được thì hủy mã để người dùng thử lại ngay
                        otpService.invalidate(email, OtpService.Purpose.RESET_PASSWORD);
                        throw ex;
                    }
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordRequestDTO request) {
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới và xác nhận mật khẩu không khớp.");
        }
        String email = request.getEmail().trim();
        // Mã chỉ được cấp cho email có tài khoản, nên kiểm tra mã trước (dùng 1 lần)
        otpService.verify(email, OtpService.Purpose.RESET_PASSWORD, request.getOtp());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Mã OTP không hợp lệ."));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        // Đăng xuất mọi thiết bị: thu hồi toàn bộ refresh token
        refreshTokenRepository.revokeAllByUserId(user.getUserId());
    }

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

        // Đăng ký không còn thu mã số thuế và địa chỉ hóa đơn.
        // Cột billing_address trên DB đang NOT NULL nên tạm lưu chuỗi rỗng.
        HorseOwnerProfile ownerProfile = HorseOwnerProfile.builder()
                .user(savedUser)
                .billingAddress("")
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
            case "Warehouse Manager":
                // Nhân viên kho chưa có bảng profile riêng, chỉ cần tài khoản Users
                break;
            default:
                throw new IllegalArgumentException("Vai trò không hợp lệ: " + request.getRoleName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponseDTO login(LoginDTO request) {
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

        // Bước 3, 4 & 5: Sinh JWT Token (chứa sub, userId, role) + refresh token và trả về kèm thông tin hạn dùng
        return buildLoginResponse(user);
    }

    @Override
    // Không rollback khi lỗi xác thực để giữ lại việc thu hồi token bị dùng lại
    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResponseDTO refreshToken(RefreshTokenRequestDTO request) {
        // Refresh token cũ bị thu hồi ngay, client phải dùng refresh token mới trả về
        RefreshToken oldToken = refreshTokenService.verifyAndRevoke(request.getRefreshToken());
        User user = oldToken.getUser();

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new DisabledException("Tài khoản đã bị vô hiệu hóa hoặc chưa được kích hoạt");
        }

        return buildLoginResponse(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(RefreshTokenRequestDTO request, String accessToken) {
        refreshTokenService.revoke(request.getRefreshToken());

        // Chặn luôn access token hiện tại; token sai hoặc đã hết hạn thì không cần chặn
        if (StringUtils.hasText(accessToken)) {
            try {
                Claims claims = jwtTokenProvider.getClaims(accessToken);
                tokenBlacklistService.revoke(claims.getId(), claims.getExpiration());
            } catch (JwtException | IllegalArgumentException ignored) {
                // Bỏ qua: token không còn dùng được nữa
            }
        }
    }

    private LoginResponseDTO buildLoginResponse(User user) {
        return LoginResponseDTO.builder()
                .accessToken(jwtTokenProvider.generateToken(user))
                .expiresIn(jwtTokenProvider.getExpirationSeconds())
                .refreshToken(refreshTokenService.createRefreshToken(user))
                .refreshExpiresIn(refreshTokenService.getRefreshExpirationSeconds())
                .role(user.getRoleName())
                .userId(user.getUserId())
                .build();
    }
}
