package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.training.dto.TrainingPlanPhaseDTO;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlanPhase;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Giai đoạn giáo án: kiểm tra khi lập, tính ngày bắt đầu / kết thúc, tìm giai đoạn chứa một ngày */
public final class TrainingPlanPhases {

    private static final Map<String, String> INTENSITY = Map.of(
            "LOW", "LOW", "THẤP", "LOW", "NHẸ", "LOW",
            "MEDIUM", "MEDIUM", "TRUNG BÌNH", "MEDIUM", "VỪA", "MEDIUM",
            "HIGH", "HIGH", "CAO", "HIGH", "NẶNG", "HIGH");
    private static final Map<String, String> TRACKS = Map.ofEntries(
            Map.entry("SYNTHETIC", "SYNTHETIC"),
            Map.entry("CỎ NHÂN TẠO", "SYNTHETIC"),
            Map.entry("CO NHAN TAO", "SYNTHETIC"),
            Map.entry("DIRT", "DIRT"),
            Map.entry("ĐẤT", "DIRT"),
            Map.entry("DAT", "DIRT"),
            Map.entry("ĐƯỜNG ĐẤT", "DIRT"),
            Map.entry("TURF", "TURF"),
            Map.entry("CỎ", "TURF"),
            Map.entry("CO", "TURF"),
            Map.entry("ĐƯỜNG CỎ", "TURF"));

    private TrainingPlanPhases() {
    }

    /** Nhẹ / Vừa / Cao (hoặc LOW / MEDIUM / HIGH) → LOW / MEDIUM / HIGH. Chuỗi trống trả về null. */
    public static String intensityOf(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String intensity = INTENSITY.get(raw.trim().toUpperCase());
        if (intensity == null) {
            throw new IllegalArgumentException("Cường độ '" + raw + "' không hợp lệ (Nhẹ / Vừa / Cao).");
        }
        return intensity;
    }

    /** Tạo các giai đoạn từ request; tổng số tuần không vượt thời gian giáo án */
    public static List<TrainingPlanPhase> build(TrainingPlan plan, List<TrainingPlanPhaseDTO> phases) {
        if (phases == null || phases.size() != 1) {
            throw new IllegalArgumentException("Mỗi giáo án chỉ gồm đúng 1 giai đoạn. Hết giai đoạn này hãy kết thúc giáo án, rồi lập giáo án mới cho giai đoạn tiếp theo.");
        }
        long planDays = ChronoUnit.DAYS.between(plan.getStartDate(), plan.getEndDate()) + 1;
        long planWeeks = (planDays + 6) / 7;
        int totalWeeks = phases.stream().mapToInt(p -> p.getWeeks() == null ? 0 : p.getWeeks()).sum();
        if (totalWeeks > planWeeks) {
            throw new IllegalArgumentException("Tổng số tuần các giai đoạn (" + totalWeeks + ") vượt thời gian giáo án ("
                    + planDays + " ngày, tối đa " + planWeeks + " tuần).");
        }
        List<TrainingPlanPhase> result = new ArrayList<>();
        int no = 1;
        for (TrainingPlanPhaseDTO p : phases) {
            String intensity = INTENSITY.get(p.getIntensity().trim().toUpperCase());
            if (intensity == null) {
                throw new IllegalArgumentException("Cường độ '" + p.getIntensity() + "' không hợp lệ (LOW / MEDIUM / HIGH).");
            }
            String track = trackCode(p.getTrackType());
            result.add(TrainingPlanPhase.builder()
                    .plan(plan)
                    .phaseNo(no++)
                    .phaseName(p.getName().trim())
                    .durationWeeks(p.getWeeks())
                    .intensity(intensity)
                    .distanceM(p.getDistanceM())
                    .targetSpeedKmh(p.getTargetSpeedKmh())
                    .trackType(track)
                    .sessionsPerWeek(p.getSessionsPerWeek())
                    .hrLimitBpm(p.getMaxHeartRate())
                    .build());
        }
        return result;
    }

    /** Mã đường SYNTHETIC / DIRT / TURF. Bắt buộc khi lập giáo án. */
    public static String trackCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("Phải chọn loại đường tập: Cỏ nhân tạo, Đường đất hoặc Đường cỏ.");
        }
        String track = TRACKS.get(raw.trim().toUpperCase(Locale.forLanguageTag("vi")));
        if (track == null) {
            throw new IllegalArgumentException("Loại đường tập '" + raw.trim()
                    + "' không hợp lệ. Chỉ dùng Cỏ nhân tạo, Đường đất hoặc Đường cỏ.");
        }
        return track;
    }

    public static String trackLabel(String code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case "SYNTHETIC" -> "Cỏ nhân tạo";
            case "DIRT" -> "Đường đất";
            case "TURF" -> "Đường cỏ";
            default -> code;
        };
    }

    /** Giai đoạn chứa ngày day (rỗng nếu giáo án không chia giai đoạn hoặc ngày nằm ngoài các giai đoạn) */
    public static Optional<TrainingPlanPhase> phaseOn(TrainingPlan plan, LocalDate day) {
        LocalDate start = plan.getStartDate();
        for (TrainingPlanPhase ph : sorted(plan)) {
            LocalDate end = start.plusWeeks(ph.getDurationWeeks());
            if (!day.isBefore(start) && day.isBefore(end)) {
                return Optional.of(ph);
            }
            start = end;
        }
        return Optional.empty();
    }

    /** Khoảng ngày [bắt đầu, kết thúc] của một giai đoạn (cắt theo ngày kết thúc giáo án) */
    public static LocalDate[] rangeOf(TrainingPlan plan, TrainingPlanPhase target) {
        LocalDate start = plan.getStartDate();
        for (TrainingPlanPhase ph : sorted(plan)) {
            LocalDate end = start.plusWeeks(ph.getDurationWeeks());
            if (ph == target) {
                LocalDate last = end.minusDays(1);
                return new LocalDate[]{start, plan.getEndDate() != null && last.isAfter(plan.getEndDate()) ? plan.getEndDate() : last};
            }
            start = end;
        }
        return new LocalDate[]{plan.getStartDate(), plan.getEndDate()};
    }

    public static List<TrainingPlanPhaseDTO> toDtos(TrainingPlan plan) {
        List<TrainingPlanPhaseDTO> list = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate start = plan.getStartDate();
        for (TrainingPlanPhase ph : sorted(plan)) {
            LocalDate end = start.plusWeeks(ph.getDurationWeeks()).minusDays(1);
            if (end.isAfter(plan.getEndDate())) {
                end = plan.getEndDate();
            }
            list.add(TrainingPlanPhaseDTO.builder()
                    .phaseNo(ph.getPhaseNo())
                    .name(ph.getPhaseName())
                    .weeks(ph.getDurationWeeks())
                    .intensity(ph.getIntensity())
                    .distanceM(ph.getDistanceM())
                    .targetSpeedKmh(ph.getTargetSpeedKmh())
                    .trackType(ph.getTrackType())
                    .sessionsPerWeek(ph.getSessionsPerWeek())
                    .maxHeartRate(ph.getHrLimitBpm())
                    .startDate(start)
                    .endDate(end)
                    .current(!today.isBefore(start) && !today.isAfter(end))
                    .build());
            start = start.plusWeeks(ph.getDurationWeeks());
        }
        return list;
    }

    private static List<TrainingPlanPhase> sorted(TrainingPlan plan) {
        if (plan.getPhases() == null) {
            return List.of();
        }
        return plan.getPhases().stream().sorted(Comparator.comparing(TrainingPlanPhase::getPhaseNo)).toList();
    }
}
