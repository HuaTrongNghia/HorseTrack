package com.horsetrack.service.impl;

import com.horsetrack.dto.TrainingPlanCreateRequestDTO;
import com.horsetrack.entity.Horse;
import com.horsetrack.entity.TrainingPlan;
import com.horsetrack.entity.TrainingStage;
import com.horsetrack.repository.HorseRepository;
import com.horsetrack.repository.TrainingPlanRepository;
import com.horsetrack.repository.TrainingStageRepository;
import com.horsetrack.service.TrainingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingPlanServiceImpl implements TrainingPlanService {

    private final TrainingPlanRepository trainingPlanRepository;
    private final TrainingStageRepository trainingStageRepository;
    private final HorseRepository horseRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer createPlanWithSessions(TrainingPlanCreateRequestDTO request) throws Exception {

        // 1. Kiểm tra Ngựa
        Horse horse = horseRepository.findById(request.getHorseId())
                .orElseThrow(() -> new Exception("Không tìm thấy ngựa với ID: " + request.getHorseId()));

        if (horse.isLocked()) {
            throw new Exception("Ngựa đang bị khóa y tế. Không thể lập giáo án (BR_01)!");
        }

        // 2. Lưu thông tin chung của Giáo án
        TrainingPlan plan = new TrainingPlan();
        plan.setHorseId(horse.getId());
        plan.setStartDate(request.getStartDate());
        plan.setPlanType(request.getPlanType());
        plan.setGoal(request.getGoal());
        plan.setPlanStatus("ACTIVE");

        // Tự động tính ngày kết thúc dựa trên tổng số tuần của các giai đoạn
        int totalWeeks = request.getStages().stream().mapToInt(stage -> stage.getWeeks()).sum();
        plan.setEndDate(request.getStartDate().plusWeeks(totalWeeks));

        // Nếu DB cũ bắt buộc (NOT NULL) các cột này, gán tạm giá trị mặc định để tránh lỗi SQL
        // ...
        plan.setPlanName(request.getPlanName());
        plan.setTrainerId(1);

        // Sửa lại thành các giá trị hợp lệ với các Check Constraint trong DB của bạn
        plan.setTargetDistanceM(1000.0); // Nếu DB cấm nhập số 0
        plan.setTargetSpeedKmh(40.0);    // Nếu DB cấm nhập số 0
        plan.setTrackType("Cát");        // HOẶC "Đường cát" (Tùy theo list dữ liệu cho phép của bạn)
        // ...

        TrainingPlan savedPlan = trainingPlanRepository.save(plan);

        // 3. Chuyển đổi DTO và lưu tất cả các Giai đoạn (Stages)
        List<TrainingStage> stages = request.getStages().stream().map(dto -> {
            TrainingStage stage = new TrainingStage();
            stage.setTrainingPlan(savedPlan);
            stage.setName(dto.getName());
            stage.setWeeks(dto.getWeeks());
            stage.setIntensity(dto.getIntensity());
            stage.setDistanceM(dto.getDistanceM());
            stage.setSessionsPerWeek(dto.getSessionsPerWeek());
            stage.setTargetBpm(dto.getTargetBpm());
            return stage;
        }).collect(Collectors.toList());

        trainingStageRepository.saveAll(stages);

        return savedPlan.getId();
    }
}