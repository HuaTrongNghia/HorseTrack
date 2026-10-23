package com.horsetrack.equinecare.modules.nutrition.service;

import com.horsetrack.equinecare.modules.nutrition.dto.StallResponseDTO;

import java.util.List;

public interface StallService {
    List<StallResponseDTO> getStallMap();

    /** Groom (chuồng trong khu mình) hoặc Club Manager đổi trạng thái chuồng */
    StallResponseDTO updateCleanStatus(Integer stallId, com.horsetrack.equinecare.modules.nutrition.dto.StallCleanStatusDTO request);

    /** Hoàn thành việc dọn chuồng: chuồng chuyển CLEAN (trừ chuồng đang bảo trì) */
    void markCleanedByTask(com.horsetrack.equinecare.modules.horse.entity.Stall stall, String taskLabel);
}