package com.horsetrack.equinecare.modules.care.service;

import com.horsetrack.equinecare.modules.care.dto.CareTaskCreateRequestDTO;
import com.horsetrack.equinecare.modules.care.dto.CareTaskDTO;

import java.util.List;

public interface CareTaskService {
    List<CareTaskDTO> getMyTasksToday();
    void completeTask(Integer taskId);
    CareTaskDTO createTask(CareTaskCreateRequestDTO request);
    /** Việc đã bị cảnh báo trễ quá 45 phút mà chưa xong (cho Club Manager theo dõi, BR_05). */
    List<CareTaskDTO> getOverdueTasks();
}
