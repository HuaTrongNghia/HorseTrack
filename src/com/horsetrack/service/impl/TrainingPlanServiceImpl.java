package com.horsetrack.service.impl;

import com.horsetrack.dto.TrainingPlanCreateRequestDTO; // Hãy đảm bảo đúng package của DTO
import com.horsetrack.entity.Horse;
import com.horsetrack.entity.TrainingPlan;
import com.horsetrack.entity.TrainingSession;
import com.horsetrack.entity.User;
import com.horsetrack.repository.HorseRepository;
import com.horsetrack.repository.TrainingPlanRepository;
import com.horsetrack.repository.TrainingSessionRepository;
import com.horsetrack.repository.UserRepository;
import com.horsetrack.service.TrainingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingPlanServiceImpl implements TrainingPlanService {

    private final TrainingPlanRepository trainingPlanRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final HorseRepository horseRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer createPlanWithSessions(TrainingPlanCreateRequestDTO request) throws Exception {

        // 1. Kiểm tra Ngựa
        Horse horse = horseRepository.findById(request.getHorseId())
                .orElseThrow(() -> new Exception("Không tìm thấy ngựa với ID: " + request.getHorseId()));

        if (horse.isLocked()) {
            throw new Exception("Ngựa đang bị khóa y tế. Không thể lập giáo án!");
        }

        // 2. Khởi tạo Giáo án (Master) khớp hoàn toàn với bảng Training_Plans
        // Khởi tạo Giáo án (Master)
        // Khởi tạo Giáo án (Master)
        TrainingPlan plan = new TrainingPlan();
        plan.setPlanName(request.getPlanName());
        plan.setHorseId(horse.getId()); // Sửa dòng này từ setHorse thành setHorseId
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());

        // Gán các giá trị hoặc giá trị mặc định cho các cột không cho phép null
        plan.setTrainerId(request.getTrainerId() != null ? request.getTrainerId() : 1);
        plan.setTargetDistanceM(request.getTargetDistanceM() != null ? request.getTargetDistanceM() : 0.0);
        plan.setTargetSpeedKmh(request.getTargetSpeedKmh() != null ? request.getTargetSpeedKmh() : 0.0);
        plan.setTrackType(request.getTrackType() != null ? request.getTrackType() : "Dirt");
        plan.setPlanStatus(request.getPlanStatus() != null ? request.getPlanStatus() : "ACTIVE");

        TrainingPlan savedPlan = trainingPlanRepository.save(plan);

        // 3. Kiểm tra danh sách nhân viên phân công trong sessions
        Set<Integer> assignedIds = request.getSessions().stream()
                .map(dto -> dto.getAssignedToId())
                .collect(Collectors.toMap(id -> id, id -> id, (existing, replacement) -> existing)).keySet(); // Hoặc dto.getAssignedToId() trực tiếp tùy DTO buổi tập

        List<User> validUsers = userRepository.findAllById(assignedIds);
        Map<Integer, User> userMap = validUsers.stream()
                .collect(Collectors.toMap(User::getUserId, u -> u));

        // 4. Chuyển đổi DTO sang Entity cho các Buổi tập
        List<TrainingSession> sessions = request.getSessions().stream().map(dto -> {
            TrainingSession session = new TrainingSession();
            session.setSessionDate(dto.getSessionDate());
            session.setActivityType(dto.getActivityType());
            session.setAssignedTo(userMap.get(dto.getAssignedToId()));
            session.setTrainingPlan(savedPlan);
            session.setStatus("PENDING");
            return session;
        }).collect(Collectors.toList());

        // 5. Lưu toàn bộ buổi tập
        trainingSessionRepository.saveAll(sessions);

        return savedPlan.getId();
    }
}