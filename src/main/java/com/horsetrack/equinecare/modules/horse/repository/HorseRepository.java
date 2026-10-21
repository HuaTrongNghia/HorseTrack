package com.horsetrack.equinecare.modules.horse.repository;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HorseRepository extends JpaRepository<Horse, Integer> {
    List<Horse> findByOwnerUserId(Integer ownerId);
    long countByOwnerUserId(Integer ownerId);
    List<Horse> findByIsLockedTrue();
    boolean existsByHorseNameAndOwner_UserId(String horseName, Integer ownerId);
    
    org.springframework.data.domain.Page<Horse> findByStallIsNull(org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Horse> findByStallIsNotNull(org.springframework.data.domain.Pageable pageable);

    // Lọc theo trạng thái hồ sơ mới nhất; ngựa chưa có hồ sơ thì suy ra từ việc đã có chuồng hay chưa
    @org.springframework.data.jpa.repository.Query("SELECT h FROM Horse h WHERE h.latestRegistrationStatus = 'PENDING_REVIEW' " +
            "OR (h.latestRegistrationStatus IS NULL AND h.stall IS NULL)")
    org.springframework.data.domain.Page<Horse> findPendingApproval(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM Horse h WHERE h.latestRegistrationStatus = 'APPROVED' " +
            "OR (h.latestRegistrationStatus IS NULL AND h.stall IS NOT NULL)")
    org.springframework.data.domain.Page<Horse> findApproved(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM Horse h WHERE h.latestRegistrationStatus = 'REJECTED'")
    org.springframework.data.domain.Page<Horse> findRejected(org.springframework.data.domain.Pageable pageable);

    boolean existsByPassportNoIgnoreCase(String passportNo);

    boolean existsByPassportNoIgnoreCaseAndHorseIdNot(String passportNo, Integer horseId);

    long countByHealthStatus(String healthStatus);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM Horse h WHERE h.isLocked = false AND h.horseId NOT IN (SELECT tp.horse.horseId FROM TrainingPlan tp WHERE tp.status = 'ACTIVE')")
    List<Horse> findAvailableHorses();
}