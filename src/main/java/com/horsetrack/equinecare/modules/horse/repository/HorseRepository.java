package com.horsetrack.equinecare.modules.horse.repository;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HorseRepository extends JpaRepository<Horse, Integer> {
    List<Horse> findByOwnerUserId(Integer ownerId);
    List<Horse> findByIsLockedTrue();
    boolean existsByHorseNameAndOwner_UserId(String horseName, Integer ownerId);
    
    org.springframework.data.domain.Page<Horse> findByStallIsNull(org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Horse> findByStallIsNotNull(org.springframework.data.domain.Pageable pageable);

    long countByHealthStatus(String healthStatus);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM Horse h WHERE h.isLocked = false AND h.horseId NOT IN (SELECT tp.horse.horseId FROM TrainingPlan tp WHERE tp.status = 'ACTIVE')")
    List<Horse> findAvailableHorses();
}