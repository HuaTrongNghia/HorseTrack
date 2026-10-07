package com.horsetrack.equinecare.modules.auth.repository;
import com.horsetrack.equinecare.modules.auth.entity.HorseOwnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
public interface HorseOwnerProfileRepository extends JpaRepository<HorseOwnerProfile, Integer> {}