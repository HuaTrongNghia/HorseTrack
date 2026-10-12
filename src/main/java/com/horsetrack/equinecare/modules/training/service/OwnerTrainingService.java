package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.training.dto.OwnerHorseListDTO;
import com.horsetrack.equinecare.modules.training.dto.OwnerTrainingProgressDTO;

import java.util.List;

public interface OwnerTrainingService {
    List<OwnerHorseListDTO> getMyHorses();
    OwnerTrainingProgressDTO getTrainingProgress(Integer horseId);
}
