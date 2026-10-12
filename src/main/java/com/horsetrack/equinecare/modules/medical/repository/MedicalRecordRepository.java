package com.horsetrack.equinecare.modules.medical.repository;
import com.horsetrack.equinecare.modules.medical.entity.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Integer> {}
