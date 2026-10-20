package com.horsetrack.equinecare.modules.care.repository;

import com.horsetrack.equinecare.modules.care.entity.CareTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CareTaskRepository extends JpaRepository<CareTask, Integer> {

    List<CareTask> findByGroom_UserIdAndTaskDateOrderByScheduledTimeAsc(Integer groomId, LocalDate taskDate);

    long countByGroom_UserIdAndStatusNot(Integer groomId, String status);
}
