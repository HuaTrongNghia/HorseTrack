package com.horsetrack.equinecare.modules.medical.repository;
import com.horsetrack.equinecare.modules.medical.entity.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Integer> {}
