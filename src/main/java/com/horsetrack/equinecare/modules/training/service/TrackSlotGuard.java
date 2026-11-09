package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import com.horsetrack.equinecare.modules.training.entity.TrainingSession;
import com.horsetrack.equinecare.modules.training.repository.TrainingSessionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Mỗi loại đường tập chỉ một ngựa trong cùng khung giờ buổi tập (giờ bắt đầu ± app.training.session-duration-minutes).
 * Giáo án vẫn chọn một loại đường; hai ngựa dùng chung đường được nếu các buổi tập không trùng giờ.
 * Khóa theo loại đường (sp_getapplock) để hai thao tác xếp buổi song song không cùng qua kiểm tra.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TrackSlotGuard {

    private static final DateTimeFormatter HM_DMY = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final TrainingSessionRepository sessionRepository;
    private final EntityManager entityManager;

    @Value("${app.training.session-duration-minutes:60}")
    private long sessionDurationMinutes;

    /** Loại đường của giáo án vào ngày của buổi tập; giáo án cũ chưa gắn đường trả về null (không kiểm tra) */
    public static String trackOn(TrainingPlan plan, LocalDateTime when) {
        if (plan == null || plan.getPhases() == null || plan.getPhases().isEmpty()) {
            return null;
        }
        return TrainingPlanPhases.phaseOn(plan, when.toLocalDate())
                .map(ph -> ph.getTrackType())
                .orElse(plan.getPhases().get(0).getTrackType());
    }

    /** Báo lỗi nếu đường của giáo án đã có ngựa khác tập trong khung giờ của buổi bắt đầu lúc start */
    public void assertFree(TrainingPlan plan, Horse horse, LocalDateTime start) {
        String track = trackOn(plan, start);
        if (track == null || horse == null || start == null) {
            return;
        }
        lock(track);
        List<TrainingSession> taken = sessionRepository.findOnTrack(track, horse.getHorseId(),
                start.minusMinutes(sessionDurationMinutes), start.plusMinutes(sessionDurationMinutes));
        if (taken.isEmpty()) {
            return;
        }
        TrainingSession other = taken.get(0);
        String otherHorse = other.getTrainingPlan() != null && other.getTrainingPlan().getHorse() != null
                ? other.getTrainingPlan().getHorse().getHorseName() : "một ngựa khác";
        throw new IllegalArgumentException(TrainingPlanPhases.trackLabel(track) + " đã có ngựa " + otherHorse + " tập lúc "
                + other.getSessionDate().format(HM_DMY) + ". Mỗi loại đường chỉ một ngựa trong cùng khung giờ "
                + sessionDurationMinutes + " phút; hãy chọn giờ khác.");
    }

    private void lock(String track) {
        Object result = entityManager.createNativeQuery("DECLARE @r INT; EXEC @r = sp_getapplock @Resource = ?1, @LockMode = 'Exclusive', "
                        + "@LockOwner = 'Transaction', @LockTimeout = 10000; SELECT @r")
                .setParameter(1, "horsetrack-track-" + track)
                .getSingleResult();
        if (result instanceof Number n && n.intValue() < 0) {
            throw new IllegalArgumentException("Đang có thao tác khác xếp lịch trên " + TrainingPlanPhases.trackLabel(track) + ". Vui lòng thử lại.");
        }
    }
}
