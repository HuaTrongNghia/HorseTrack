package com.horsetrack.equinecare.modules.auth.service;

import com.horsetrack.equinecare.modules.auth.dto.RegisterRequestDTO;
import com.horsetrack.equinecare.modules.auth.entity.*;
// Các import cho Profile Entities và Repositories (Bạn cần tạo thêm các Entity này tương ứng với Data4.txt)
import com.horsetrack.equinecare.modules.auth.repository.*;
// Giả định import: import com.horsetrack.equinecare.modules.profile.entity.HorseOwnerProfile;
// Giả định import: import com.horsetrack.equinecare.modules.profile.repository.HorseOwnerProfileRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final HorseOwnerProfileRepository horseOwnerRepo;
    private final ClubManagerProfileRepository managerRepo;
    private final TrainerProfileRepository trainerRepo;
    private final VeterinarianProfileRepository vetRepo;
    private final GroomProfileRepository groomRepo;

    // Khai báo các repository cho Profile (Cần tạo các interface này)
    // private final HorseOwnerProfileRepository horseOwnerRepo;
    // private final ClubManagerProfileRepository managerRepo;
    // ... thêm các repo khác tương tự

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           HorseOwnerProfileRepository horseOwnerRepo, ClubManagerProfileRepository managerRepo,
                           TrainerProfileRepository trainerRepo, VeterinarianProfileRepository vetRepo,
                           GroomProfileRepository groomRepo) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.horseOwnerRepo = horseOwnerRepo;
        this.managerRepo = managerRepo;
        this.trainerRepo = trainerRepo;
        this.vetRepo = vetRepo;
        this.groomRepo = groomRepo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class) // Đảm bảo nếu lỗi ở bước tạo Profile thì bước tạo User cũng bị rollback
    public User register(RegisterRequestDTO request) {
        try {
            // 1. Kiểm tra xác nhận mật khẩu từ giao diện
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                throw new IllegalArgumentException("Mật khẩu và Xác nhận mật khẩu không khớp.");
            }

            // 2. Kiểm tra Unique Constraints (Username, Email)
            if (userRepository.existsByUsername(request.getUsername().trim())) {
                throw new IllegalArgumentException("Tên đăng nhập đã tồn tại.");
            }
            if (userRepository.existsByEmail(request.getEmail().trim())) {
                throw new IllegalArgumentException("Email đã được sử dụng.");
            }

            // 3. Khởi tạo đối tượng User cơ sở
            User newUser = User.builder()
                    .username(request.getUsername().trim())
                    .fullName(request.getFullName().trim())
                    .email(request.getEmail().trim())
                    .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                    .passwordHash(passwordEncoder.encode(request.getPassword())) // Băm mật khẩu BCrypt (Work Factor 12)
                    .roleName(request.getRoleName()) // Sẽ bị ràng buộc bởi CHK_User_Role ở DB
                    .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                    .createdAt(LocalDateTime.now())
                    .build();

            // 4. Lưu User vào DB để lấy sinh ra user_id (Tự động Map từ IDENTITY)
            User savedUser = userRepository.save(newUser);

            // 5. Khởi tạo Hồ sơ Chuyên môn (Sub-Profiles) dựa trên Role Name
            createSubProfileBasedOnRole(savedUser, request);

            return savedUser;

        } catch (IllegalArgumentException ex) {
            throw ex; // Lỗi nghiệp vụ, ném ra để Controller trả về 400 Bad Request
        } catch (Exception ex) {
            throw new RuntimeException("Lỗi hệ thống khi đăng ký và cấp phát hồ sơ: " + ex.getMessage(), ex);
        }
    }

    /**
     * Hàm phụ trợ cấp phát hồ sơ 1-1 tương ứng với vai trò.
     */
    private void createSubProfileBasedOnRole(User user, RegisterRequestDTO request) {
        switch (user.getRoleName()) {
            case "Horse Owner":
                if (request.getBillingAddress() == null || request.getBillingAddress().isBlank()) {
                    throw new IllegalArgumentException("Chủ ngựa bắt buộc phải cung cấp Địa chỉ xuất hóa đơn.");
                }
                HorseOwnerProfile ownerProfile = HorseOwnerProfile.builder()
                        .user(user)
                        .taxCode(request.getTaxCode())
                        .billingAddress(request.getBillingAddress())
                        .build();
                horseOwnerRepo.save(ownerProfile);
                break;

            case "Club Manager":
                ClubManagerProfile managerProfile = ClubManagerProfile.builder()
                        .user(user)
                        .department(request.getDepartment() != null ? request.getDepartment() : "General Management")
                        .approvalLimit(java.math.BigDecimal.ZERO) // Mặc định là 0
                        .build();
                managerRepo.save(managerProfile);
                break;

            case "Veterinarian":
                if (request.getVetLicenseNo() == null || request.getVetLicenseNo().isBlank()) {
                    throw new IllegalArgumentException("Bác sĩ thú y cần cung cấp số giấy phép hành nghề.");
                }
                VeterinarianProfile vetProfile = VeterinarianProfile.builder()
                        .user(user)
                        .vetLicenseNo(request.getVetLicenseNo())
                        .clinicAffiliation(request.getClinicAffiliation())
                        .build();
                vetRepo.save(vetProfile);
                break;

            case "Head Trainer":
                if (request.getTrainerLicenseNumber() == null || request.getTrainerLicenseNumber().isBlank()) {
                    throw new IllegalArgumentException("Huấn luyện viên cần cung cấp số giấy phép.");
                }
                TrainerProfile trainerProfile = TrainerProfile.builder()
                        .user(user)
                        .licenseNumber(request.getTrainerLicenseNumber())
                        .experienceYears(request.getExperienceYears() != null ? request.getExperienceYears() : 0)
                        .build();
                trainerRepo.save(trainerProfile);
                break;

            case "Groom":
                GroomProfile groomProfile = GroomProfile.builder()
                        .user(user)
                        .assignedBarnZone(request.getAssignedBarnZone())
                        .shiftType(request.getShiftType())
                        .build();
                groomRepo.save(groomProfile);
                break;

            default:
                throw new IllegalArgumentException("Vai trò không hợp lệ theo chuẩn CSDL: " + user.getRoleName());
        }

    }
}