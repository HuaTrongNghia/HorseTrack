package com.horsetrack.equinecare.modules.auth.repository;

import com.horsetrack.equinecare.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

    // THÊM HÀM NÀY ĐỂ LẤY DANH SÁCH GROOM/TRAINER CHO DROPDOWN
    List<User> findByRoleNameInAndIsActiveTrue(List<String> roleNames);
}
