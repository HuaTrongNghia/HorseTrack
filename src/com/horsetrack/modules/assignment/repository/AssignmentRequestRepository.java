package com.horsetrack.equinecare.modules.assignment.repository;

import com.horsetrack.equinecare.modules.assignment.entity.AssignmentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentRequestRepository extends JpaRepository<AssignmentRequest, Integer> {

    List<AssignmentRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<AssignmentRequest> findByStatusAndTargetUserIdOrderByCreatedAtDesc(String status, Integer targetUserId);

    List<AssignmentRequest> findByStatusAndHorseId(String status, Integer horseId);

    List<AssignmentRequest> findByStatusAndTargetUserIdAndRequestType(String status, Integer targetUserId, String requestType);
}
