package com.horsetrack.equinecare.modules.assignment.service;

import com.horsetrack.equinecare.modules.assignment.dto.AssignmentRequestResponseDTO;
import com.horsetrack.equinecare.modules.assignment.entity.AssignmentRequest;
import com.horsetrack.equinecare.modules.auth.service.StaffAssignmentService;
import com.horsetrack.equinecare.modules.horse.service.HorseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Nhân viên xác nhận thì phân công mới được ghi. Từ chối thì giữ nguyên và lưu lý do. */
@Service
@RequiredArgsConstructor
public class AssignmentDecisionService {

    private final AssignmentRequestWriter writer;
    private final HorseService horseService;
    private final StaffAssignmentService staffAssignmentService;

    @Transactional(readOnly = true)
    public List<AssignmentRequestResponseDTO> mine() {
        return writer.pendingFor(writer.currentUserId());
    }

    @Transactional(readOnly = true)
    public List<AssignmentRequestResponseDTO> pending() {
        return writer.allPending();
    }

    @Transactional
    public void accept(Integer requestId) {
        AssignmentRequest req = writer.requireMine(requestId);
        switch (req.getRequestType()) {
            case AssignmentRequestWriter.TRAINER_HORSE -> horseService.applyTrainerAssignment(req.getHorseId(), req.getTargetUserId());
            case AssignmentRequestWriter.TRAINER_RELEASE -> horseService.applyTrainerRelease(req.getHorseId());
            case AssignmentRequestWriter.GROOM_ZONE -> staffAssignmentService.applyGroomZone(req.getTargetUserId(), req.getBarnZone());
            default -> throw new IllegalArgumentException("Loại yêu cầu không hỗ trợ: " + req.getRequestType());
        }
        writer.mark(req, "ACCEPTED", null);
    }

    @Transactional
    public void reject(Integer requestId, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) throw new IllegalArgumentException("Phải ghi lý do.");
        AssignmentRequest req = writer.requireMine(requestId);
        writer.mark(req, "REJECTED", why);
        writer.mailRejected(req, why);
    }
}
