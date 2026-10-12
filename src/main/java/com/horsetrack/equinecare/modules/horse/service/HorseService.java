package com.horsetrack.equinecare.modules.horse.service;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import java.util.List;

public interface HorseService {
    java.util.List<com.horsetrack.equinecare.modules.horse.dto.HorseResponseDTO> getMyHorses(Integer ownerId);
    void lockHorseTraining(Integer horseId, String reason);
    com.horsetrack.equinecare.modules.horse.dto.HorseResponseDTO registerHorse(com.horsetrack.equinecare.modules.horse.dto.HorseRegistrationRequestDTO request);
    
    org.springframework.data.domain.Page<com.horsetrack.equinecare.modules.horse.dto.HorseResponseDTO> getAllHorses(int page, int size, String status);
    com.horsetrack.equinecare.modules.horse.dto.HorseResponseDTO getHorseDetail(Integer horseId);
    com.horsetrack.equinecare.modules.horse.dto.HorseResponseDTO approveHorse(Integer horseId, com.horsetrack.equinecare.modules.horse.dto.HorseApprovalRequestDTO request);
    com.horsetrack.equinecare.modules.horse.dto.HorseResponseDTO updateHorse(Integer horseId, com.horsetrack.equinecare.modules.horse.dto.HorseUpdateRequestDTO request);
}