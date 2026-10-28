package com.horsetrack.equinecare.modules.care.repository;

import com.horsetrack.equinecare.modules.care.entity.CareTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CareTaskRepository extends JpaRepository<CareTask, Integer> {

    List<CareTask> findByGroom_UserIdAndTaskDateOrderByScheduledTimeAsc(Integer groomId, LocalDate taskDate);

    // Việc chăm sóc của các ngựa HLV phụ trách trong một ngày (HLV theo dõi việc đã giao)
    List<CareTask> findByHorse_AssignedTrainer_UserIdAndTaskDateOrderByScheduledTimeAsc(Integer trainerId, LocalDate taskDate);

    List<CareTask> findByTaskDateOrderByScheduledTimeAsc(LocalDate taskDate);

    // Việc trễ SLA trong một ngày (đang mở hoặc đã hoàn thành trễ), trừ việc đã hủy
    List<CareTask> findByTaskDateAndWarningNoteIsNotNullAndStatusNotOrderByScheduledTimeAsc(LocalDate taskDate, String status);

    long countByGroom_UserIdAndStatusNot(Integer groomId, String status);

    // Việc cho ăn sinh từ khẩu phần (ghi chú bắt đầu bằng KP-planId-MEAL:)
    List<CareTask> findByHorse_HorseIdAndTaskDateAndTaskType(Integer horseId, LocalDate taskDate, String taskType);

    List<CareTask> findByNotesStartingWithAndStatusAndTaskDateGreaterThanEqual(String notesPrefix, String status, LocalDate fromDate);

    // Việc chăm sóc chưa xong từ hôm nay trở đi của một Groom (chặn đổi / bỏ khu khi còn việc dở)
    long countByGroom_UserIdAndStatusInAndTaskDateGreaterThanEqual(Integer groomId, java.util.Collection<String> statuses, LocalDate fromDate);

    // Việc chưa xong, chưa bị cảnh báo, có ngày làm từ hôm nay trở về trước (lọc giờ trễ ở service)
    List<CareTask> findByStatusInAndWarningNoteIsNullAndTaskDateLessThanEqual(java.util.Collection<String> statuses, LocalDate taskDate);

    // Việc đã bị cảnh báo trễ và vẫn chưa xong
    List<CareTask> findByWarningNoteIsNotNullAndStatusInOrderByTaskDateAscScheduledTimeAsc(java.util.Collection<String> statuses);
}
