package com.horsetrack.equinecare.modules.nutrition.repository;

import com.horsetrack.equinecare.modules.nutrition.entity.DietChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DietChangeRequestRepository extends JpaRepository<DietChangeRequest, Integer> {

    List<DietChangeRequest> findAllByOrderByRequestedAtDesc();

    List<DietChangeRequest> findByHorse_HorseIdOrderByRequestedAtDesc(Integer horseId);

    List<DietChangeRequest> findByStatusOrderByRequestedAtDesc(String status);

    List<DietChangeRequest> findByHorse_HorseIdAndStatusOrderByRequestedAtDesc(Integer horseId, String status);

    List<DietChangeRequest> findByRequestedBy_UserIdOrderByRequestedAtDesc(Integer userId);
}
