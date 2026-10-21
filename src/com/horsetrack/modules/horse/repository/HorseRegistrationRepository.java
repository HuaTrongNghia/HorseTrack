package com.horsetrack.equinecare.modules.horse.repository;

import com.horsetrack.equinecare.modules.horse.entity.HorseRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HorseRegistrationRepository extends JpaRepository<HorseRegistration, Integer> {

    Optional<HorseRegistration> findFirstByHorse_HorseIdOrderByRegistrationIdDesc(Integer horseId);

    List<HorseRegistration> findByHorse_HorseIdOrderByRegistrationIdDesc(Integer horseId);
}
