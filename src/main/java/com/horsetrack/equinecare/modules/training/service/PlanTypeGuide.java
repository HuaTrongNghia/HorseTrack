package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.training.dto.PlanStaffGuideDTO;
import com.horsetrack.equinecare.modules.training.entity.TrainingPlan;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hướng dẫn theo loại giáo án: việc Mã phu phải làm và các bước trong buổi tập.
 * 4 loại có hướng dẫn cố định; loại "Khác" dùng mô tả và các bước HLV tự viết (lưu trên giáo án).
 */
public final class PlanTypeGuide {

    public record Text(String type, String summary, List<String> steps) {
    }

    /** Kết quả kiểm tra loại giáo án khi lập / sửa: loại chuẩn và phần tự viết (chỉ có với loại Khác) */
    public record Checked(String type, String customSummary, String customSteps) {
    }

    public static final String OTHER = "Khác";
    private static final int MAX_STEPS = 15;
    private static final int MAX_STEP_LENGTH = 300;
    private static final int MAX_SUMMARY_LENGTH = 1000;
    private static final String TYPES = "Sức bền, Tốc độ, Phục hồi, Kỹ thuật hoặc Khác";

    private static final List<Text> ALL = List.of(
            new Text("Sức bền",
                    "Mã phu đưa ngựa ra đúng loại đường của giáo án và giữ nhịp đều suốt buổi. Không thúc tốc độ. Mục tiêu là duy trì thể lực, không phải chạy hết sức.",
                    List.of(
                            "Dắt ngựa bước đi 5 phút trên đúng loại đường của giáo án để khởi động.",
                            "Chuyển sang nước kiệu hoặc phi nhẹ theo cường độ ghi trên giáo án và giữ đều đến gần cuối buổi.",
                            "Đo nhịp tim và tốc độ theo các mốc buổi tập. Nếu vượt ngưỡng, dừng bài và báo huấn luyện viên.",
                            "Hạ tốc độ dần trong 5 phút cuối, rồi dắt bộ về chuồng.",
                            "Ghi đủ chỉ số vào phiên trước khi kết thúc buổi.")),
            new Text("Tốc độ",
                    "Mã phu cho ngựa chạy các đoạn nhanh ngắn và cho nghỉ giữa các đoạn. Không giữ tốc độ cao liên tục.",
                    List.of(
                            "Khởi động bước và kiệu 8–10 phút trên đúng loại đường.",
                            "Chạy các đoạn phi nhanh ngắn theo cường độ giáo án, xen kẽ đi bộ để hồi sức.",
                            "Đo nhịp tim sau mỗi đoạn nhanh. Gần ngưỡng thì ngừng đoạn nhanh và chuyển sang đi bộ.",
                            "Kết thúc bằng đi bộ thả lỏng đến khi nhịp tim hạ, rồi đưa về chuồng.",
                            "Ghi từng lần đo vào phiên.")),
            new Text("Phục hồi",
                    "Mã phu chỉ cho ngựa vận động rất nhẹ để hồi phục. Không phi nước đại và không đổi hướng đột ngột.",
                    List.of(
                            "Dắt bước hoặc cho kiệu rất nhẹ trên đúng loại đường.",
                            "Giữ cường độ nhẹ suốt buổi, dù ngựa có vẻ muốn chạy nhanh hơn.",
                            "Quan sát dáng đi. Nếu ngựa khập khiễng hoặc miễn cưỡng, dừng ngay và báo huấn luyện viên.",
                            "Đo nhịp tim hồi phục khi đứng nghỉ cuối buổi.",
                            "Đưa về chuồng và ghi chỉ số vào phiên.")),
            new Text("Kỹ thuật",
                    "Mã phu tập xuất phát, vào cua và giữ hàng theo hiệu lệnh. Tốc độ phục vụ bài kỹ thuật, không phải bài đua tốc độ.",
                    List.of(
                            "Khởi động đi bộ trên đúng loại đường.",
                            "Tập xuất phát đúng hiệu lệnh, vào cua và giữ hàng theo nội dung giáo án.",
                            "Giữ tốc độ ở mức giáo án cho phép. Không tự nâng lên phi nước đại.",
                            "Dừng bài nếu ngựa lệch hướng, mất kiểm soát hoặc vượt ngưỡng nhịp tim, rồi báo huấn luyện viên.",
                            "Thả lỏng bằng đi bộ và ghi chỉ số trước khi về chuồng.")));

    private static final Map<String, Text> BY_KEY = ALL.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                    t -> key(t.type()), t -> t));

    private PlanTypeGuide() {
    }

    /** Loại giáo án hợp lệ, đúng tên chuẩn. Báo lỗi nếu trống hoặc không thuộc 4 loại chuẩn. */
    public static Text require(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Chọn loại giáo án: " + TYPES + ".");
        }
        Text found = BY_KEY.get(key(raw));
        if (found == null) {
            throw new IllegalArgumentException("Loại giáo án '" + raw.trim() + "' không hợp lệ. Chỉ dùng " + TYPES + ".");
        }
        return found;
    }

    /**
     * Kiểm tra loại giáo án khi HLV lập / sửa. Loại "Khác" bắt buộc có mô tả và ít nhất một bước;
     * các loại chuẩn bỏ qua phần tự viết (dùng hướng dẫn cố định).
     */
    public static Checked require(String raw, String customSummary, List<String> customSteps) {
        if (!isOther(raw)) {
            return new Checked(require(raw).type(), null, null);
        }
        if (customSummary == null || customSummary.isBlank()) {
            throw new IllegalArgumentException("Giáo án loại Khác phải có mô tả (customSummary): Mã phu cần làm gì trong buổi tập.");
        }
        String summary = customSummary.trim();
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            throw new IllegalArgumentException("Mô tả giáo án Khác không được vượt quá " + MAX_SUMMARY_LENGTH + " ký tự.");
        }
        List<String> steps = customSteps == null ? List.of() : customSteps.stream()
                .filter(st -> st != null && !st.isBlank())
                .map(st -> st.replaceAll("\\s*[\\r\\n]+\\s*", " ").trim())
                .toList();
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Giáo án loại Khác phải có ít nhất một bước (customSteps) để Mã phu làm theo.");
        }
        if (steps.size() > MAX_STEPS) {
            throw new IllegalArgumentException("Giáo án loại Khác tối đa " + MAX_STEPS + " bước.");
        }
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).length() > MAX_STEP_LENGTH) {
                throw new IllegalArgumentException("Bước " + (i + 1) + " dài quá " + MAX_STEP_LENGTH + " ký tự.");
            }
        }
        return new Checked(OTHER, summary, String.join("\n", steps));
    }

    public static boolean isOther(String raw) {
        if (raw == null) {
            return false;
        }
        String k = key(raw);
        return k.equals(key(OTHER)) || k.equals("khac") || k.equals("other");
    }

    /** Hướng dẫn của một giáo án: loại Khác lấy phần HLV tự viết, loại chuẩn lấy hướng dẫn cố định */
    public static Text forPlan(TrainingPlan plan) {
        if (plan == null) {
            return null;
        }
        if (isOther(plan.getPlanType())) {
            List<String> steps = plan.getCustomSteps() == null || plan.getCustomSteps().isBlank() ? List.of()
                    : java.util.Arrays.stream(plan.getCustomSteps().split("\\R")).filter(st -> !st.isBlank()).toList();
            return new Text(OTHER, plan.getCustomSummary(), steps);
        }
        return find(plan.getPlanType());
    }

    public static PlanStaffGuideDTO toDto(TrainingPlan plan) {
        Text found = forPlan(plan);
        if (found == null) {
            return null;
        }
        return PlanStaffGuideDTO.builder().summary(found.summary()).steps(found.steps()).build();
    }

    public static Text find(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return BY_KEY.get(key(raw));
    }

    public static PlanStaffGuideDTO toDto(String planType) {
        Text found = find(planType);
        if (found == null) {
            return null;
        }
        return PlanStaffGuideDTO.builder().summary(found.summary()).steps(found.steps()).build();
    }

    private static String key(String raw) {
        return raw.trim().toLowerCase(Locale.forLanguageTag("vi"));
    }
}
