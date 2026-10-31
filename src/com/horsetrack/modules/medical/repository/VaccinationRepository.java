package com.horsetrack.equinecare.modules.medical.repository;

import com.horsetrack.equinecare.modules.medical.entity.Vaccination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VaccinationRepository extends JpaRepository<Vaccination, Integer> {

    List<Vaccination> findAllByOrderByDueDateAscVaccinationIdAsc();

    List<Vaccination> findByHorse_HorseIdOrderByDueDateAscVaccinationIdAsc(Integer horseId);

  
