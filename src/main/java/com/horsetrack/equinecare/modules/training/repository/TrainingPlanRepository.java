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

    // Phạm vi của Trainer: giáo án của các ngựa được phân công cho Trainer đó
    Page<TrainingPlan> findByHorse_AssignedTrainer_UserId(Integer trainerId, Pageable pageable);

    Page<TrainingPlan> findByHorse_AssignedTrainer_UserIdAndStatus(Integer trainerId, String status, Pageable pageable);

    long countByHorse_AssignedTrainer_UserIdAndStatus(Integer trainerId, String status);

    // Giáo án đang chờ, đã có buổi tập được Groom xác nhận và đã tới ngày bắt đầu
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s.trainingPlan FROM TrainingSession s WHERE s.status = 'CONFIRMED' " +
            "AND s.trainingPlan.status = 'PENDING' AND s.trainingPlan.startDate <= :today")
    java.util.List<TrainingPlan> findReadyToActivate(@org.springframework.data.repository.query.Param("today") java.time.LocalDate today);
}
