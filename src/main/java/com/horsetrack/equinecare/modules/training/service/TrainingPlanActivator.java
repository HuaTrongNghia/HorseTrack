package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import com.horsetrack.equinecare.modules.training.repository.TrainingPlanRepository;
import com.horsetrack.equinecare.modules.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Vòng đời giáo án: HLV lập giáo án → PENDING (đang chờ). Giáo án chỉ chuyển sang ACTIVE (đang chạy) khi
 * đã có ít nhất một buổi tập được Groom xác nhận (CONFIRMED) VÀ đã tới ngày bắt đầu của giáo án.
 * Kiểm tra ngay khi Groom xác nhận, và định kỳ cho các giáo án có ngày bắt đầu tới sau đó.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingPlanActivator {

    public static final String PLAN_PENDING = "PENDING";
    public static final String PLAN_ACTIVE = "ACTIVE";
    public static final String SESSION_PENDING = "PENDING";
    public static final String SESSION_CONFIRMED = "CONFIRMED";
    /** Buổi tập dừng giữa chừng (chỉ số vượt ngưỡng), đã gửi ca cho bác sĩ, chờ bác sĩ quyết định */
    public static final String SESSION_WAITING_VET = "WAITING_VET";
    /** Buổi tập kết thúc do dừng giữa chừng */
    public static final String SESSION_STOPPED = "STOPPED";
    /** Groom đã bắt đầu buổi tập, đang nhập chỉ số */
    public static final String SESSION_IN_PROGRESS = "IN_PROGRESS";
    /** Chỉ số vượt ngưỡng: buổi tập tự tạm dừng, chờ HLV cho tập tiếp hoặc dừng & gửi bác sĩ (F22-02) */
    public static final String SESSION_PAUSED_PENDING_TRAINER = "PAUSED_PENDING_TRAINER";
    /** Buổi tập chưa kết thúc (kể cả đang chờ HLV / bác sĩ): giáo án chưa được tự hoàn thành */
    public static final List<String> UNFINISHED_SESSION_STATUSES = List.of("PENDING", "CONFIRMED", SESSION_IN_PROGRESS, SESSION_PAUSED_PENDING_TRAINER, "WAITING_VET");
    /** Buổi tập chưa kết thúc và còn thực hiện tiếp được (chờ xác nhận, đã xác nhận, đang tập) */
    public static final List<String> OPEN_SESSION_STATUSES = List.of(SESSION_PENDING, SESSION_CONFIRMED, SESSION_IN_PROGRESS);
    /** Buổi tập đang chiếm lịch của Groom / ngựa (để chặn trùng giờ, hủy khi ngựa bị khóa) */
    public static final List<String> ACTIVE_SESSION_STATUSES = List.of(SESSION_PENDING, SESSION_CONFIRMED, SESSION_IN_PROGRESS, SESSION_PAUSED_PENDING_TRAINER);
    /** Giáo án đang chiếm ngựa: mỗi ngựa chỉ có tối đa một giáo án ở các trạng thái này */
    public static final List<String> OPEN_PLAN_STATUSES = List.of(PLAN_PENDING, PLAN_ACTIVE, "PAUSED");

    private final TrainingPlanRepository planRepository;
    private final TrainingSessionRepository sessionRepository;

    /** Chuyển giáo án PENDING sang ACTIVE nếu đủ điều kiện. Trả về true khi vừa được kích hoạt. */
    @Transactional
    public boolean tryActivate(TrainingPlan plan, LocalDate today) {
        if (plan == null || !PLAN_PENDING.equalsIgnoreCase(plan.getStatus())) {
            return false;
        }
        if (plan.getStartDate() != null && plan.getStartDate().isAfter(today)) {
            return false;
        }
        if (!sessionRepository.existsByTrainingPlan_PlanIdAndStatus(plan.getPlanId(), SESSION_CONFIRMED)) {
            return false;
        }
        plan.setStatus(PLAN_ACTIVE);
        planRepository.save(plan);
        log.info("Giáo án {} chuyển sang ACTIVE (đã có buổi tập được xác nhận, tới ngày bắt đầu {})", plan.getPlanId(), plan.getStartDate());
        return true;
    }

    @Scheduled(fixedDelayString = "${app.training.activate-check-ms:300000}", initialDelayString = "${app.training.activate-initial-delay-ms:30000}")
    @Transactional
    public void activateDuePlans() {
        int n = activateDuePlans(LocalDate.now());
        if (n > 0) {
            log.info("Đã kích hoạt {} giáo án tới ngày bắt đầu", n);
        }
    }

    /** Kích hoạt mọi giáo án đang chờ đã đủ điều kiện tại ngày today. */
    @Transactional
    public int activateDuePlans(LocalDate today) {
        int count = 0;
        for (TrainingPlan plan : planRepository.findReadyToActivate(today)) {
            if (tryActivate(plan, today)) {
                count++;
            }
        }
        return count;
    }
}
