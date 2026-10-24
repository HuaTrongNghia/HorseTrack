package com.horsetrack.equinecare.modules.auth.repository;

import com.horsetrack.equinecare.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    // QA H3: khóa dòng tới hết transaction để các yêu cầu đồng thời trên cùng nhân viên chạy lần lượt
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT e FROM User e WHERE e.userId = :id")
    java.util.Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);

    // Kiểm tra trùng Email
    boolean existsByEmail(String email);

    // GEN-01: số điện thoại không được trùng giữa các tài khoản
    boolean existsByPhone(String phone);

    // Ảnh đại diện (data URL) đọc / ghi riêng, không map vào entity để không nạp chuỗi lớn mỗi lần đọc User
    @org.springframework.data.jpa.repository.Query(value = "SELECT avatar_url FROM Users WHERE user_id = :userId", nativeQuery = true)
    String findAvatarUrl(@org.springframework.data.repository.query.Param("userId") Integer userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "UPDATE Users SET avatar_url = :avatarUrl WHERE user_id = :userId", nativeQuery = true)
    int updateAvatarUrl(@org.springframework.data.repository.query.Param("userId") Integer userId,
                        @org.springframework.data.repository.query.Param("avatarUrl") String avatarUrl);

    // Kiểm tra trùng Username
    boolean existsByUsername(String username);

    // Tìm kiếm phục vụ đăng nhập sau này
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

    // Đăng nhập bằng số điện thoại (Users.phone là duy nhất)
    Optional<User> findByPhone(String phone);

    // Lấy danh sách nhân sự theo vai trò và đang hoạt động
    List<User> findByRoleNameInAndIsActiveTrue(List<String> roleNames);

    List<User> findAllByOrderByUserIdDesc();

    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE " +
            "(:search IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            " OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) " +
            " OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:roleName IS NULL OR u.roleName = :roleName) " +
            "AND (:isActive IS NULL OR u.isActive = :isActive) " +
            "ORDER BY u.userId DESC")
    org.springframework.data.domain.Page<User> searchUsers(
            @org.springframework.data.repository.query.Param("search") String search,
            @org.springframework.data.repository.query.Param("roleName") String roleName,
            @org.springframework.data.repository.query.Param("isActive") Boolean isActive,
            org.springframework.data.domain.Pageable pageable);
}