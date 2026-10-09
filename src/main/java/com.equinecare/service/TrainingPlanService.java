package com.equinecare.service;

import com.equinecare.dto.request.TrainingPlanCreateRequest;
import com.equinecare.dto.request.TrainingDayCreateRequest;
import com.equinecare.entity.Horse;
import com.equinecare.entity.User;
import com.equinecare.entity.TrainingPlan;
import com.equinecare.entity.TrainingDay;
import com.equinecare.repository.HorseRepository;
import com.equinecare.repository.UserRepository;
import com.equinecare.repository.TrainingPlanRepository;
import com.equinecare.repository.TrainingDayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TrainingPlanService {

    @Autowired
    private HorseRepository horseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrainingPlanRepository trainingPlanRepository;

    @Autowired
    private TrainingDayRepository trainingDayRepository;

    @Autowired
    private NotificationService notificationService;

    // Lấy danh sách Groom đổ ra màn hình Lịch (Calendar)
    public List<User> getActiveGrooms() {
        return userRepository.findByRoleNameAndIsActiveTrue("Groom");
    }

    // BƯỚC 1: Lập thông tin Giáo án chung
    @Transactional
    public TrainingPlan executeTrainingPlanFlow(TrainingPlanCreateRequest request) {

        Horse horse = horseRepository.findById(request.getHorseId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin ngựa."));

        // KIỂM TRA HỆ THỐNG Y TẾ (Vetlock check)
        if (horse.getIsLocked()) {
            notificationService.sendToTrainer(request.getTrainerId(),
                    "CẢNH BÁO CHẶN: Ngựa " + horse.getHorseId() + " đang bị VetLock. Vui lòng đổi lịch tập hoặc cho nghỉ.");
            notificationService.sendToOwner(horse.getOwnerId(),
                    "THÔNG BÁO: Buổi tập của chiến mã đã bị dừng khẩn cấp do yêu cầu y tế.");
            throw new RuntimeException("VETLOCK_ACTIVE");
        }

        // Kiểm tra tính hợp lệ của ngày
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new RuntimeException("Ngày kết thúc không được nhỏ hơn ngày bắt đầu.");
        }

        // Lưu kế hoạch
        TrainingPlan plan = new TrainingPlan();
        plan.setHorseId(horse.getHorseId());
        plan.setTrainerId(request.getTrainerId());
        plan.setPlanName(request.getPlanName());
        plan.setTargetDistanceM(request.getTargetDistanceM());
        plan.setTargetSpeedKmh(request.getTargetSpeedKmh());
        plan.setTrackType(request.getTrackType());
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setPlanStatus("Active");

        return trainingPlanRepository.save(plan);
    }

    // BƯỚC 2: Thêm từng buổi tập vào lịch và phân công Groom
    @Transactional
    public TrainingDay addSessionToPlan(Integer planId, TrainingDayCreateRequest request) {
        TrainingPlan plan = trainingPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giáo án này."));

        // Lại phải kiểm tra VetLock phòng khi trong lúc lên lịch ngựa bị khóa y tế
        Horse horse = horseRepository.findById(plan.getHorseId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy dữ liệu ngựa."));

        if (horse.getIsLocked()) {
            throw new RuntimeException("VETLOCK_ACTIVE");
        }

        TrainingDay day = new TrainingDay();
        day.setPlanId(planId);
        day.setTrainerId(request.getTrainerId());
        day.setGroomId(request.getGroomId());
        day.setTrainingDate(request.getTrainingDate());

        if (request.getDurationMinutes() != null) {
            day.setDurationMinutes(request.getDurationMinutes());
        }

        TrainingDay savedDay = trainingDayRepository.save(day);

        // Gửi thông báo cho Groom
        notificationService.sendToGroom(request.getGroomId(),
                "NHIỆM VỤ MỚI: Bạn được phân công dắt ngựa vào ngày " + request.getTrainingDate());

        return savedDay;
    }
}