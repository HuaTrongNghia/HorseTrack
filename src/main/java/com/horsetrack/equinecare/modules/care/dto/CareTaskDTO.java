package com.horsetrack.equinecare.modules.care.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareTaskDTO {
    private Integer taskId;
    private String taskCode;
    private String taskType;
    private String title;
    private String status;
    private LocalDate taskDate;
    private LocalTime scheduledTime;
    private String instructions;
    private LocalDateTime completedAt;
    private Integer horseId;
    private String horseName;
    private String stallCode;
    private String stallZone;
    private Integer groomId;
    private String groomName;
    private String warningNote;   // Có giá trị khi việc bị cảnh báo trễ quá 45 phút (BR_05)
    private Boolean overdue;
    private Integer lateMinutes;   // số phút trễ so với giờ hẹn khi đã quá SLA (tính tới lúc hoàn thành, hoặc tới hiện tại nếu chưa xong); null nếu không trễ SLA
    private String dietNote;       // Lưu ý đặc biệt của khẩu phần (việc cho ăn)
    private Boolean medicalDiet;   // Khẩu phần y tế do bác sĩ chỉ định
    private LocalDateTime completableFrom;   // thời điểm sớm nhất Groom được xác nhận hoàn thành (giờ hẹn - app.care.early-complete-minutes)
    private Boolean canComplete;             // việc đang mở và đã tới khung giờ được xác nhận
}
