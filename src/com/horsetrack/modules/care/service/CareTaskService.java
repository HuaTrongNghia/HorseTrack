package com.horsetrack.equinecare.modules.care.service;

import com.horsetrack.equinecare.modules.care.dto.CareTaskCreateRequestDTO;
import com.horsetrack.equinecare.modules.care.dto.CareTaskDTO;

import java.util.List;

public interface CareTaskService {
    List<CareTaskDTO> getMyTasksToday();
    /** Groom: việc của mình trong một ngày (mặc định hôm nay), xem trước được các ngày tới */
    List<CareTaskDTO> getMyTasks(java.time.LocalDate date);
    void completeTask(Integer taskId);
    /** HLV: việc chăm sóc của ngựa mình phụ trách trong ngày (mặc định hôm nay); Club Manager: mọi ngựa. Lọc trạng thái tùy chọn. */
    List<CareTaskDTO> getAssignedTasks(java.time.LocalDate date, String status);
    CareTaskDTO createTask(CareTaskCreateRequestDTO request);
    /** Việc đã bị cảnh báo trễ quá 45 phút mà chưa xong (cho Club Manager theo dõi, BR_05). */
    List<CareTaskDTO> getOverdueTasks();
    /** Mọi việc trễ SLA của một ngày: còn mở hoặc đã hoàn thành trễ (thống kê "trễ hôm nay"). */
    List<CareTaskDTO> getLateTasksOn(java.time.LocalDate date);
}
