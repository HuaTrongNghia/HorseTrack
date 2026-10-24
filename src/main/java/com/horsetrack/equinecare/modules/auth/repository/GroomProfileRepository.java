package com.horsetrack.equinecare.modules.auth.repository;
import com.horsetrack.equinecare.modules.auth.entity.GroomProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroomProfileRepository extends JpaRepository<GroomProfile, Integer> {

    // Groom đang hoạt động, đã xác nhận tài khoản, phụ trách một khu chuồng (so khớp không phân biệt hoa thường, bỏ khoảng trắng đầu/cuối)
    @Query("SELECT gp FROM GroomProfile gp JOIN FETCH gp.user u " +
            "WHERE u.roleName = 'Groom' AND u.isActive = true AND u.emailVerifiedAt IS NOT NULL " +
            "AND LOWER(TRIM(gp.assignedBarnZone)) = LOWER(TRIM(:barnZone)) " +
            "ORDER BY u.fullName")
    List<GroomProfile> findActiveGroomsInZone(@Param("barnZone") String barnZone);
}
