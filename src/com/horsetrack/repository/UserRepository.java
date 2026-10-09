package com.horsetrack.repository;

import com.horsetrack.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional; // Nhớ import thư viện này

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    // Thêm dòng này để phục vụ chức năng Đăng nhập
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
}