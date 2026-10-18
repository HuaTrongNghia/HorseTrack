package com.horsetrack.equinecare.modules.care.service;

import com.horsetrack.equinecare.modules.care.dto.CareTaskDTO;
import java.util.List;

public interface CareTaskService {
    List<CareTaskDTO> getMyTasksToday();
    void completeTask(Integer taskId);
}
