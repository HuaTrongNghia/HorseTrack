package com.horsetrack.repository;

import com.horsetrack.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MedicationRepository extends JpaRepository<Medication, Integer> {
    // Spring Data JPA sẽ tự động cung cấp các hàm findAll(), findById(), save(), delete()...
}
