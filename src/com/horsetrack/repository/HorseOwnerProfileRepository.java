package com.horsetrack.repository;

import com.horsetrack.entity.HorseOwnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HorseOwnerProfileRepository extends JpaRepository<HorseOwnerProfile, Integer> {
}