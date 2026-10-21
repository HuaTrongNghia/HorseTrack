package com.horsetrack.equinecare.modules.auth.repository;

import com.horsetrack.equinecare.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    // Kiểm tra trùng Email
    boolean existsByEmail(String email);

    // GEN-01: số điện thoại không được trùng giữa các tài khoản
    boolean existsByPhone(String phone);

    // Kiểm tra trùng Username
    boolean existsByUsername(String username);

    // Tìm kiếm phục vụ đăng nhập sau này
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

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
