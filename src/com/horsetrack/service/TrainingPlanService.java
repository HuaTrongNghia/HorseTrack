package com.horsetrack.service;

import com.horsetrack.dto.TrainingPlanCreateRequestDTO;

public interface TrainingPlanService {
    Integer createPlanWithSessions(TrainingPlanCreateRequestDTO request) throws Exception;
}