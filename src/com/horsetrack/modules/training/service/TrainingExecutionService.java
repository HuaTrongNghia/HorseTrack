package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.training.dto.SessionExecuteRequestDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainerScheduleDTO;

import java.time.LocalDate;
import java.util.List;

public interface TrainingExecutionService {
    List<TrainerScheduleDTO> getMySchedule(LocalDate startDate, LocalDate endDate);
    void executeSession(Integer sessionId, SessionExecuteRequestDTO request);
}
