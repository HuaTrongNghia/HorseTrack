package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.modules.auth.dto.ForgotPasswordRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.ResetPasswordRequestDTO;
import com.horsetrack.equinecare.modules.auth.security.OtpService;
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
    private final com.horsetrack.equinecare.modules.audit.service.AuditTrail auditTrail;
    private final com.horsetrack.equinecare.modules.notification.service.EmailDomainVerifier emailDomainVerifier;
    private final StaffAccountService staffAccountService;

    /** Số điện thoại Việt Nam: 0xxxxxxxxx hoặc +84xxxxxxxxx */
    private static final java.util.regex.Pattern PHONE = java.util.regex.Pattern.compile("^(0|\\+84)\\d{9,10}$");

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
                    // Gửi ở nền, không chờ: email có hay không tồn tại đều phản hồi ngay và giống nhau.
                    // Gửi không được thì hủy mã + thời gian chờ để người dùng thử lại ngay.
                    emailService.sendOtp(user.getEmail(), user.getFullName(), otp, OtpService.Purpose.RESET_PASSWORD.getLabel())
                            .exceptionally(ex -> {
                                otpService.invalidate(email, OtpService.Purpose.RESET_PASSWORD);
                                return null;
                            });
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

    // ==========================================
    // ĐĂNG KÝ CHỦ NGỰA (xác thực email bằng OTP)
    // ==========================================

    @Override
    public String requestOwnerRegistrationOtp(OwnerRegisterRequestDTO request) {
        String email = normalizeEmail(request.getEmail());
        checkOwnerUnique(request.getUsername().trim(), email, normalizePhone(request.getPhone()));
        // Tên miền không nhận thư (gõ sai, không tồn tại) thì báo ngay
        emailDomainVerifier.checkDeliverable(email);
        otpService.checkAndMarkCooldown(email, OtpService.Purpose.REGISTER);
        String otp = otpService.issue(email, OtpService.Purpose.REGISTER);
        java.util.concurrent.CompletableFuture<Void> sending = emailService.sendOtp(email, request.getFullName().trim(), otp, OtpService.Purpose.REGISTER.getLabel())
                .whenComplete((ok, ex) -> {
                    if (ex != null) {
                        otpService.invalidate(email, OtpService.Purpose.REGISTER);
                    }
                });
        // Máy chủ thư từ chối địa chỉ thì báo lại ngay cho người đăng ký
        if (emailService.await(sending) == EmailService.Delivery.FAILED) {
            otpService.invalidate(email, OtpService.Purpose.REGISTER);
            throw new IllegalArgumentException("Không gửi được mã OTP tới " + email + ". Email có thể không tồn tại, vui lòng kiểm tra lại.");
        }
        return OtpService.maskEmail(email);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerOwner(OwnerRegisterRequestDTO request) {
        String email = normalizeEmail(request.getEmail());
        String phone = normalizePhone(request.getPhone());
        checkOwnerUnique(request.getUsername().trim(), email, phone);
        if (!StringUtils.hasText(request.getOtp())) {
            throw new IllegalArgumentException("Thiếu mã OTP xác thực email.");
        }
        otpService.verify(email, OtpService.Purpose.REGISTER, request.getOtp().trim());

        User newUser = User.builder()
                .username(request.getUsername().trim())
                .fullName(request.getFullName().trim())
                .email(email)
                .phone(phone)
                .emailVerifiedAt(LocalDateTime.now())
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
        auditTrail.record("REGISTER_OWNER", "Users", null,
                "Chủ ngựa tự đăng ký " + savedUser.getUsername() + " (user_id=" + savedUser.getUserId() + ", email đã xác thực OTP)");
    }

    // Không trùng tên đăng nhập, email, số điện thoại (không kiểm tra trùng họ tên)
    private void checkOwnerUnique(String username, String email, String phone) {
        if (PHONE.matcher(username).matches()) {
            throw new IllegalArgumentException("Tên đăng nhập không được có dạng số điện thoại.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email đã được sử dụng.");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("Số điện thoại đã được sử dụng.");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    // Bỏ khoảng trắng, dấu chấm, gạch; +84 -> 0
    public static String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String p = phone.trim().replaceAll("[\\s.\\-]", "");
        return p.startsWith("+84") ? "0" + p.substring(3) : p;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StaffAccountService.CreatedStaff createStaff(StaffCreateRequestDTO request) {
        String email = normalizeEmail(request.getEmail());
        String phone = normalizePhone(request.getPhone());
        String username = request.getUsername().trim();
        if (PHONE.matcher(username).matches()) {
            throw new IllegalArgumentException("Tên đăng nhập không được có dạng số điện thoại.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email đã được sử dụng.");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("Số điện thoại đã được sử dụng.");
        }
        String rawPassword = request.getPassword();

        User newUser = User.builder()
                .username(username)
                .fullName(request.getFullName().trim())
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .roleName(request.getRoleName())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
        // Chưa xác nhận: chưa đăng nhập, chưa được phân công tới khi nhân viên nhấn nút trong email
        String rawToken = staffAccountService.newConfirmationToken(newUser);

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
                // Khu chuồng phân công sau khi Groom xác nhận tài khoản (PUT /users/{id}/barn-zone)
                GroomProfile groomProfile = GroomProfile.builder()
                .user(savedUser)
                .build();
                groomRepo.save(groomProfile);
                break;
            case "Warehouse Manager":
                // Nhân viên kho chưa có bảng profile riêng, chỉ cần tài khoản Users
                break;
            default:
                throw new IllegalArgumentException("Vai trò không hợp lệ: " + request.getRoleName());
        }
        auditTrail.record("CREATE_STAFF", "Users", null,
                "Tạo tài khoản " + savedUser.getEmail() + " (user_id=" + savedUser.getUserId() + ", vai trò " + request.getRoleName() + ") - chờ nhân viên xác nhận qua email");
        return new StaffAccountService.CreatedStaff(savedUser, rawPassword, rawToken);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponseDTO login(LoginDTO request) {
        // Bước 1: tìm tài khoản theo tên đăng nhập, email hoặc số điện thoại
        User user = findByLoginId(request.getIdentifier().trim())
                .orElseThrow(() -> new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác"));

        // Kiểm tra xem tài khoản có đang hoạt động hay không
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new DisabledException("Tài khoản đã bị vô hiệu hóa hoặc chưa được kích hoạt");
        }

        // Bước 2: So khớp mật khẩu raw với password_hash từ Database bằng PasswordEncoder
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }
        // Nhân viên chưa nhấn "Xác nhận tài khoản" trong email
        if (user.getEmailVerifiedAt() == null) {
            throw new DisabledException("Tài khoản chưa được xác nhận. Hãy mở email từ HorseTrack và nhấn \"Xác nhận tài khoản\".");
        }

        // Bước 3, 4 & 5: Sinh JWT Token (chứa sub, userId, role) + refresh token và trả về kèm thông tin hạn dùng
        return buildLoginResponse(user);
    }

    private java.util.Optional<User> findByLoginId(String loginId) {
        if (loginId.contains("@")) {
            return userRepository.findByEmail(normalizeEmail(loginId));
        }
        java.util.Optional<User> byUsername = userRepository.findByUsername(loginId);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        String phone = normalizePhone(loginId);
        return phone != null && PHONE.matcher(phone).matches() ? userRepository.findByPhone(phone) : java.util.Optional.empty();
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
