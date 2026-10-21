package com.horsetrack.equinecare.modules.training.repository;

import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Integer> {
    long countBySessionDateBetween(LocalDateTime startOfDay, LocalDateTime endOfDay);

    long countByTrainingPlan_Horse_AssignedTrainer_UserIdAndSessionDateBetween(Integer trainerId, LocalDateTime startOfDay, LocalDateTime endOfDay);
    
    List<TrainingSession> findByAssignedTo_UserIdAndSessionDateBetweenOrderBySessionDateAsc(Integer assignedToId, LocalDateTime startDate, LocalDateTime endDate);
    
    long countByTrainingPlan_PlanIdAndStatus(Integer planId, String status);

    long countByTrainingPlan_PlanIdAndStatusIn(Integer planId, java.util.Collection<String> statuses);

    // Buổi tập chưa thực hiện, chưa qua giờ của một nhân viên
    long countByAssignedTo_UserIdAndStatusInAndSessionDateAfter(Integer userId, java.util.Collection<String> statuses, LocalDateTime from);

    boolean existsByTrainingPlan_PlanIdAndStatus(Integer planId, String status);

    // F21-05: buổi tập chưa kết thúc (PENDING / CONFIRMED) của một nhân viên bắt đầu trong khoảng (from, to) -> trùng khung giờ
    @org.springframework.data.jpa.repository.Query("SELECT s FROM TrainingSession s WHERE s.assignedTo.userId = :userId " +
            "AND s.status IN ('PENDING', 'CONFIRMED') AND s.sessionDate > :from AND s.sessionDate < :to ORDER BY s.sessionDate")
    List<TrainingSession> findOverlapping(@org.springframework.data.repository.query.Param("userId") Integer userId,
                                         @org.springframework.data.repository.query.Param("from") LocalDateTime from,
                                         @org.springframework.data.repository.query.Param("to") LocalDateTime to);

    // F3-03: các buổi tập còn chờ của một con ngựa (để hủy khi ngựa bị VetLock)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM TrainingSession s WHERE s.trainingPlan.horse.horseId = :horseId " +
            "AND s.status IN ('PENDING', 'CONFIRMED') ORDER BY s.sessionDate")
    List<TrainingSession> findPendingByHorse(@org.springframework.data.repository.query.Param("horseId") Integer horseId);
}
