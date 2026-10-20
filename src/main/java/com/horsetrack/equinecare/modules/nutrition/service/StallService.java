package com.horsetrack.equinecare.modules.nutrition.service;

import com.horsetrack.equinecare.modules.nutrition.dto.StallResponseDTO;

import java.util.List;

public interface StallService {
    List<StallResponseDTO> getStallMap();
}