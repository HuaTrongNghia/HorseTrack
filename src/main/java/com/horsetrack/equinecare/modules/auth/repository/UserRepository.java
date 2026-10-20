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

    // Kiểm tra trùng Username
    boolean existsByUsername(String username);

    // Tìm kiếm phục vụ đăng nhập sau này
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

    // Lấy danh sách nhân sự theo vai trò và đang hoạt động
    List<User> findByRoleNameInAndIsActiveTrue(List<String> roleNames);
}