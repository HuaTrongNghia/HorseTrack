package com.horsetrack.equinecare.modules.auth.repository;
import com.horsetrack.equinecare.modules.auth.entity.TrainerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TrainerProfileRepository extends JpaRepository<TrainerProfile, Integer> {}