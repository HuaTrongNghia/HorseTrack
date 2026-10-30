package com.horsetrack.equinecare.modules.tournament.service;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.tournament.dto.HorseEligibilityDTO;
import com.horsetrack.equinecare.modules.tournament.entity.CompetitionEntry;
import com.horsetrack.equinecare.modules.tournament.entity.ExternalCompetition;
import com.horsetrack.equinecare.modules.tournament.repository.CompetitionEntryRepository;
import com.horsetrack.equinecare.modules.training.service.SessionEvaluationHelper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;

/**
 * Lọc điều kiện ngựa cho giải: hồ sơ đã duyệt, có HLV phụ trách, tuổi, cân nặng, giống, không khóa y tế,
 * không đang điều trị, không trùng ngày thi giải khác. Mỗi tiêu chí trả về đạt / không đạt kèm lý do.
 * Phong độ (0-100) tính từ 10 buổi tập gần nhất trong 90 ngày: tốc độ tối đa 40%, nhịp tim hồi phục 20%,
 * tỉ lệ hoàn thành buổi tập 15%, điểm HLV chấm 25% (thiếu chỉ số nào thì chia lại tỉ trọng cho các chỉ số còn lại).
 */
@Service
@RequiredArgsConstructor
public class HorseEligibilityService {

    private static final int FORM_DAYS = 90;
    private static final int FORM_SESSIONS = 10;
    // Mốc quy đổi: tốc độ tối đa 65 km/h = 100 điểm; nhịp hồi phục 60 bpm = 100 điểm, 140 bpm = 0 điểm
    private static final double SPEED_FULL = 65.0;
    private static final double RECOVERY_BEST = 60.0;
    private static final double RECOVERY_WORST = 140.0;

    private final CompetitionEntryRepository entryRepository;
    private final EntityManager entityManager;

    public record Form(BigDecimal score, String note) {
    }

    @Transactional(readOnly = true)
    public HorseEligibilityDTO evaluate(Horse h, ExternalCompetition c) {
        HorseEligibilityDTO dto = checks(h, c);
        Form form = form(h.getHorseId());
        dto.setFormScore(form.score());
        dto.setFormNote(form.note());
        return dto;
    }

    /** Chỉ lọc điều kiện (không tính phong độ) */
    @Transactional(readOnly = true)
    public HorseEligibilityDTO checks(Horse h, ExternalCompetition c) {
        List<HorseEligibilityDTO.Check> checks = new ArrayList<>();
        LocalDate raceDate = c.getStartDate();
        Integer age = h.getDob() != null ? Period.between(h.getDob(), raceDate).getYears() : null;

        boolean approved = Horse.APPROVAL_APPROVED.equals(h.getApprovalStatus()) && h.getStall() != null;
        checks.add(check("APPROVED", "Hồ sơ đã duyệt", approved,
                approved ? "Đã duyệt và có chuồng " + h.getStall().getStallCode() : "Hồ sơ ngựa chưa được duyệt / chưa được cấp chuồng"));

        boolean hasTrainer = h.getAssignedTrainer() != null;
        checks.add(check("HAS_TRAINER", "Có HLV phụ trách", hasTrainer,
                hasTrainer ? "HLV " + h.getAssignedTrainer().getFullName() : "Ngựa chưa được phân công HLV để xác nhận đề xuất"));

        if (c.getMinAgeYears() != null || c.getMaxAgeYears() != null) {
            String range = range(c.getMinAgeYears(), c.getMaxAgeYears(), " tuổi");
            boolean ok = age != null && (c.getMinAgeYears() == null || age >= c.getMinAgeYears()) && (c.getMaxAgeYears() == null || age <= c.getMaxAgeYears());
            checks.add(check("AGE", "Tuổi " + range, ok,
                    age == null ? "Chưa có ngày sinh của ngựa" : age + " tuổi vào ngày thi" + (ok ? "" : ", yêu cầu " + range)));
        }

        if (c.getMinWeightKg() != null || c.getMaxWeightKg() != null) {
            String range = range(c.getMinWeightKg(), c.getMaxWeightKg(), " kg");
            BigDecimal w = h.getWeightKg();
            boolean ok = w != null && (c.getMinWeightKg() == null || w.compareTo(c.getMinWeightKg()) >= 0)
                    && (c.getMaxWeightKg() == null || w.compareTo(c.getMaxWeightKg()) <= 0);
            checks.add(check("WEIGHT", "Cân nặng " + range, ok,
                    w == null ? "Chưa có cân nặng của ngựa" : num(w) + " kg" + (ok ? "" : ", yêu cầu " + range)));
        }

        List<String> breeds = breeds(c.getAllowedBreeds());
        if (!breeds.isEmpty()) {
            boolean ok = h.getBreed() != null && breeds.stream().anyMatch(b -> b.equalsIgnoreCase(h.getBreed().trim()));
            checks.add(check("BREED", "Giống " + String.join(", ", breeds), ok,
                    h.getBreed() == null ? "Chưa có thông tin giống" : "Giống " + h.getBreed() + (ok ? "" : ", giải chỉ nhận " + String.join(", ", breeds))));
        }

        boolean unlocked = !Boolean.TRUE.equals(h.getIsLocked());
        checks.add(check("NOT_LOCKED", "Không bị khóa y tế", unlocked, unlocked ? "Không bị khóa" : "Ngựa đang bị khóa y tế (VetLock)"));

        String protocol = openProtocol(h.getHorseId());
        checks.add(check("NOT_IN_TREATMENT", "Không đang điều trị", protocol == null,
                protocol == null ? "Không có phác đồ điều trị đang mở" : "Đang có phác đồ điều trị " + protocol));

        List<CompetitionEntry> sameDay = entryRepository.findSameDayEntries(h.getHorseId(), raceDate, c.getCompetitionId(), CompetitionEntry.ACTIVE);
        checks.add(check("NO_DATE_CONFLICT", "Không trùng ngày thi giải khác", sameDay.isEmpty(),
                sameDay.isEmpty() ? "Không trùng lịch" : "Đã có đề xuất ở giải " + sameDay.get(0).getCompetition().getCompetitionName() + " cùng ngày " + raceDate));

        List<String> reasons = checks.stream().filter(k -> !k.getPassed()).map(k -> k.getLabel() + ": " + k.getDetail()).toList();
        return HorseEligibilityDTO.builder()
                .horseId(h.getHorseId())
                .horseCode(h.getHorseCode())
                .horseName(h.getHorseName())
                .breed(h.getBreed())
                .ageYears(age)
                .weightKg(h.getWeightKg())
                .eligible(reasons.isEmpty())
                .checks(checks)
                .reasons(reasons)
                .recommended(false)
                .build();
    }

    /** Lý do không đủ điều kiện (rỗng = đủ điều kiện) */
    @Transactional(readOnly = true)
    public List<String> failures(Horse h, ExternalCompetition c) {
        return checks(h, c).getReasons();
    }

    /** Như failures nhưng bỏ qua một số tiêu chí (VD: HAS_TRAINER khi HLV đã xác nhận xong) */
    @Transactional(readOnly = true)
    public List<String> failures(Horse h, ExternalCompetition c, Set<String> ignoreCodes) {
        return checks(h, c).getChecks().stream()
                .filter(k -> !k.getPassed() && !ignoreCodes.contains(k.getCode()))
                .map(k -> k.getLabel() + ": " + k.getDetail()).toList();
    }

    /** Đánh giá các ngựa của chủ và xếp hạng: phù hợp trước, phong độ giảm dần; ngựa phù hợp đứng đầu được gợi ý */
    @Transactional(readOnly = true)
    public List<HorseEligibilityDTO> rank(List<Horse> horses, ExternalCompetition c) {
        List<HorseEligibilityDTO> list = new ArrayList<>(horses.stream().map(h -> evaluate(h, c)).toList());
        Comparator<HorseEligibilityDTO> byForm = Comparator.comparing(HorseEligibilityDTO::getFormScore, Comparator.nullsLast(Comparator.reverseOrder()));
        List<HorseEligibilityDTO> byScore = list.stream().sorted(byForm.thenComparing(HorseEligibilityDTO::getHorseName, Comparator.nullsLast(String::compareToIgnoreCase))).toList();
        for (int i = 0; i < byScore.size(); i++) {
            byScore.get(i).setFormRank(i + 1);
        }
        list.sort(Comparator.comparing((HorseEligibilityDTO d) -> !d.getEligible()).thenComparing(HorseEligibilityDTO::getFormRank));
        list.stream().filter(HorseEligibilityDTO::getEligible).findFirst().ifPresent(d -> d.setRecommended(true));
        return list;
    }

    /** Phong độ từ dữ liệu buổi tập (null nếu chưa có buổi tập nào trong 90 ngày) */
    @Transactional(readOnly = true)
    public Form form(Integer horseId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT TOP %d s.status, s.notes,
                       (SELECT MAX(r.speed_kmh) FROM Session_Readings r WHERE r.session_id = s.session_id AND r.reading_phase = 'SESSION') top_speed,
                       (SELECT AVG(CAST(r.heart_rate_bpm AS FLOAT)) FROM Session_Readings r WHERE r.session_id = s.session_id AND r.reading_phase = 'RECOVERY') recovery_hr
                FROM Training_Sessions s JOIN Training_Plans p ON p.plan_id = s.plan_id
                WHERE p.horse_id = ?1 AND s.status IN ('COMPLETED', 'STOPPED') AND s.session_date >= ?2
                ORDER BY s.session_date DESC""".formatted(FORM_SESSIONS))
                .setParameter(1, horseId)
                .setParameter(2, LocalDate.now().minusDays(FORM_DAYS).atStartOfDay())
                .getResultList();
        if (rows.isEmpty()) {
            return new Form(null, "Chưa có buổi tập trong " + FORM_DAYS + " ngày gần nhất");
        }
        int completed = 0;
        List<Double> speeds = new ArrayList<>();
        List<Double> recoveries = new ArrayList<>();
        List<Integer> evals = new ArrayList<>();
        for (Object[] r : rows) {
            if ("COMPLETED".equals(r[0])) completed++;
            var eval = SessionEvaluationHelper.extractEvaluation(r[1] == null ? null : r[1].toString());
            if (eval != null && eval.getScore() != null) evals.add(eval.getScore());
            if (r[2] != null && ((Number) r[2]).doubleValue() > 0) speeds.add(((Number) r[2]).doubleValue());
            if (r[3] != null) recoveries.add(((Number) r[3]).doubleValue());
        }
        double weighted = 0, weights = 0;
        List<String> parts = new ArrayList<>();
        if (!speeds.isEmpty()) {
            double v = avg(speeds);
            weighted += 40 * clamp(v / SPEED_FULL * 100);
            weights += 40;
            parts.add("tốc độ tối đa TB " + round1(v) + " km/h");
        }
        if (!recoveries.isEmpty()) {
            double v = avg(recoveries);
            weighted += 20 * clamp((RECOVERY_WORST - v) / (RECOVERY_WORST - RECOVERY_BEST) * 100);
            weights += 20;
            parts.add("nhịp hồi phục TB " + Math.round(v) + " bpm");
        }
        weighted += 15 * (100.0 * completed / rows.size());
        weights += 15;
        parts.add("hoàn thành " + completed + "/" + rows.size() + " buổi");
        if (!evals.isEmpty()) {
            double v = evals.stream().mapToInt(Integer::intValue).average().orElse(0);
            weighted += 25 * clamp(v * 10);
            weights += 25;
            parts.add("điểm HLV TB " + round1(v) + "/10");
        }
        BigDecimal score = BigDecimal.valueOf(weighted / weights).setScale(1, RoundingMode.HALF_UP);
        return new Form(score, rows.size() + " buổi tập gần nhất: " + String.join(", ", parts));
    }

    public static List<String> breeds(String stored) {
        if (!StringUtils.hasText(stored)) {
            return List.of();
        }
        return Arrays.stream(stored.split(";")).map(String::trim).filter(StringUtils::hasText).toList();
    }

    private String openProtocol(Integer horseId) {
        List<?> list = entityManager.createNativeQuery("SELECT TOP 1 protocol_code FROM Treatment_Protocols WHERE horse_id = ?1 "
                        + "AND state NOT IN ('COMPLETED', 'SUPERSEDED', 'CANCELLED') ORDER BY protocol_id DESC")
                .setParameter(1, horseId).getResultList();
        return list.isEmpty() ? null : String.valueOf(list.get(0));
    }

    private static HorseEligibilityDTO.Check check(String code, String label, boolean passed, String detail) {
        return HorseEligibilityDTO.Check.builder().code(code).label(label).passed(passed).detail(detail).build();
    }

    private static String range(Object min, Object max, String unit) {
        String a = min instanceof BigDecimal b ? num(b) : String.valueOf(min);
        String z = max instanceof BigDecimal b ? num(b) : String.valueOf(max);
        if (min != null && max != null) return "từ " + a + " đến " + z + unit;
        return min != null ? "từ " + a + unit + " trở lên" : "tối đa " + z + unit;
    }

    private static String num(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static double avg(List<Double> v) {
        return v.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
