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

    // Việc chưa xong, chưa bị cảnh báo, có ngày làm từ hôm nay trở về trước (lọc giờ trễ ở service)
    List<CareTask> findByStatusInAndWarningNoteIsNullAndTaskDateLessThanEqual(java.util.Collection<String> statuses, LocalDate taskDate);

    // Việc đã bị cảnh báo trễ và vẫn chưa xong
    List<CareTask> findByWarningNoteIsNotNullAndStatusInOrderByTaskDateAscScheduledTimeAsc(java.util.Collection<String> statuses);
}
