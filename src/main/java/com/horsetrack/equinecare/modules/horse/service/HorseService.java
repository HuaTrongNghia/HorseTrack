package com.horsetrack.equinecare.modules.horse.service;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import java.util.List;

public interface HorseService {
    List<Horse> getHorsesByOwner(Integer ownerId);
    void lockHorseTraining(Integer horseId, String reason);
}