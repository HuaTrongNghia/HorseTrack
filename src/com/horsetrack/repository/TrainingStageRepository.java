package com.horsetrack.repository;

import com.horsetrack.entity.TrainingStage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingStageRepository extends JpaRepository<TrainingStage, Integer> {
}