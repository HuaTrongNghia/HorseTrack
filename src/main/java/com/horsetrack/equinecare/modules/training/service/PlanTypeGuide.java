package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.training.dto.PlanStaffGuideDTO;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Hướng dẫn cố định theo loại giáo án: việc Mã phu phải làm và các bước trong buổi tập. */
public final class PlanTypeGuide {

    public record Text(String type, String summary, List<String> steps) {
    }

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

    /** Loại giáo án hợp lệ, đúng tên chuẩn. Báo lỗi nếu trống hoặc không thuộc 4 loại. */
    public static Text require(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Chọn loại giáo án: Sức bền, Tốc độ, Phục hồi hoặc Kỹ thuật.");
        }
        Text found = BY_KEY.get(key(raw));
        if (found == null) {
            throw new IllegalArgumentException("Loại giáo án '" + raw.trim()
                    + "' không có hướng dẫn. Chỉ dùng Sức bền, Tốc độ, Phục hồi hoặc Kỹ thuật.");
        }
        return found;
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
