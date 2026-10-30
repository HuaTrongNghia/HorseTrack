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

    Optional<Vaccination> findByVaccinationCode(String vaccinationCode);

    // Lịch tiêm phòng / tẩy giun còn chờ của một ngày (nhắc bác sĩ)
    List<Vaccination> findByStatusAndDueDateOrderByKindAscVaccinationIdAsc(String status, java.time.LocalDate dueDate);

    boolean existsByHorse_HorseIdAndVaccineNameIgnoreCaseAndStatusIn(Integer horseId, String vaccineName, Collection<String> statuses);
}
