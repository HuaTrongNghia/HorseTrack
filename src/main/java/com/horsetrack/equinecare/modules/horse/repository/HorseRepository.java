package com.horsetrack.equinecare.modules.horse.repository;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HorseRepository extends JpaRepository<Horse, Integer> {
    List<Horse> findByOwnerUserId(Integer ownerId);
    List<Horse> findByIsLockedTrue();
}