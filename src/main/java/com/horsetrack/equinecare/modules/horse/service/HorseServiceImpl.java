package com.horsetrack.equinecare.modules.horse.service;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HorseServiceImpl implements HorseService {

    private final HorseRepository horseRepository;

    // Constructor injection thuần
    public HorseServiceImpl(HorseRepository horseRepository) {
        this.horseRepository = horseRepository;
    }

    @Override
    public List<Horse> getHorsesByOwner(Integer ownerId) {
        return horseRepository.findByOwnerUserId(ownerId);
    }

    @Override
    @Transactional
    public void lockHorseTraining(Integer horseId, String reason) {
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new RuntimeException("Horse not found"));
        horse.setLocked(true);
        horseRepository.save(horse);
    }
}