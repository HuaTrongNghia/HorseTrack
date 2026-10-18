package com.horsetrack.equinecare.modules.care.repository;

import com.horsetrack.equinecare.modules.care.entity.CareTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface CareTaskRepository extends JpaRepository<CareTask, Integer> {
    List<CareTask> findByAssignedTo_UserIdAndDueDateBetweenOrderByDueDateAsc(Integer userId, LocalDateTime start, LocalDateTime end);
}
