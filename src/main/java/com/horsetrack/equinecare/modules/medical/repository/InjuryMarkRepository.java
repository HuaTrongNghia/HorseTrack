package com.horsetrack.equinecare.modules.medical.repository;
import com.horsetrack.equinecare.modules.medical.entity.InjuryMark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface InjuryMarkRepository extends JpaRepository<InjuryMark, Integer> {}
