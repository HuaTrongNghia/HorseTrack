package com.horsetrack.equinecare.modules.training.repository;

import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Integer> {
    Page<TrainingPlan> findAll(Pageable pageable);
    Page<TrainingPlan> findByStatus(String status, Pageable pageable);
    long countByStatus(String status);
    Optional<TrainingPlan> findFirstByHorse_HorseIdAndStatusOrderByCreatedAtDesc(Integer horseId, String status);

    boolean existsByHorse_HorseIdAndStatus(Integer horseId, String status);

    boolean existsByHorse_HorseIdAndStatusIn(Integer horseId, java.util.Collection<String> statuses);

    java.util.List<TrainingPlan> findByHorse_HorseIdAndStatus(Integer horseId, String status);

    Optional<TrainingPlan> findFirstByHorse_HorseIdAndStatusInOrderByCreatedAtDesc(Integer horseId, java.util.Collection<String> statuses);

    Optional<TrainingPlan> findFirstByHorse_HorseIdOrderByCreatedAtDesc(Integer horseId);

    java.util.List<TrainingPlan> findByHorse_HorseIdOrderByCreatedAtDesc(Integer horseId);

    // Phạm vi của Trainer: giáo án của các ngựa được phân công cho Trainer đó
    Page<TrainingPlan> findByHorse_AssignedTrainer_UserId(Integer trainerId, Pageable pageable);

    Page<TrainingPlan> findByHorse_AssignedTrainer_UserIdAndStatus(Integer trainerId, String status, Pageable pageable);

    long countByHorse_AssignedTrainer_UserIdAndStatus(Integer trainerId, String status);

    /** Giáo án đang mở (theo status) có cùng loại đường và khoảng ngày giao với [startDate, endDate], trừ chính ngựa đang lập. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT p FROM TrainingPlan p
            WHERE p.status IN :statuses
              AND p.startDate <= :endDate
              AND p.endDate >= :startDate
              AND p.horse.horseId <> :horseId
              AND EXISTS (
                SELECT ph.phaseId FROM TrainingPlanPhase ph
                WHERE ph.plan = p AND ph.trackType = :trackType)
            """)
    java.util.List<TrainingPlan> findOpenOnTrack(
            @org.springframework.data.repository.query.Param("trackType") String trackType,
            @org.springframework.data.repository.query.Param("statuses") java.util.Collection<String> statuses,
            @org.springframework.data.repository.query.Param("endDate") java.time.LocalDate endDate,
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDate startDate,
            @org.springframework.data.repository.query.Param("horseId") Integer horseId);

    // Giáo án đang chờ, đã có buổi tập được Groom xác nhận và đã tới ngày bắt đầu
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s.trainingPlan FROM TrainingSession s WHERE s.status = 'CONFIRMED' " +
            "AND s.trainingPlan.status = 'PENDING' AND s.trainingPlan.startDate <= :today")
    java.util.List<TrainingPlan> findReadyToActivate(@org.springframework.data.repository.query.Param("today") java.time.LocalDate today);
}
