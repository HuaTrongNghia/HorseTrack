package com.horsetrack.equinecare.modules.care.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.entity.Stall;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Công việc chăm sóc hằng ngày của Groom, lưu trong bảng Daily_Tasks.
 * Các cột service_fee, is_medical_diet... không map ở đây để DB tự lấy giá trị mặc định.
 */
@Entity
@Table(name = "Daily_Tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Integer taskId;

    // Cột tính toán do DB tự sinh (TK-0001), chỉ đọc
    @Column(name = "task_code", insertable = false, updatable = false)
    private String taskCode;

    @Column(name = "task_type", nullable = false, length = 100)
    private String taskType; // FEED, CLEAN, GROOM, WALK, COOL, HOOF, MEDICATION, OTHER

    @Column(name = "title", length = 300)
    private String title;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Column(name = "scheduled_time", nullable = false)
    private LocalTime scheduledTime;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // TODO, IN_PROGRESS, DONE, SKIPPED

    @Column(name = "instructions", columnDefinition = "NVARCHAR(MAX)")
    private String instructions;

    @Column(name = "notes", length = 2000)
    private String notes;

    // Cảnh báo trễ hạn (BR_05 / F4-04): hệ thống ghi khi quá 45 phút sau giờ hẹn mà chưa xong; khác null thì không cảnh báo lặp
    // Lưu ý đặc biệt của khẩu phần (ví dụ thức ăn bác sĩ duyệt thêm) hiển thị cho Groom
    @Column(name = "diet_note", length = 255)
    private String dietNote;

    // Việc cho ăn theo khẩu phần y tế của bác sĩ
    @Column(name = "is_medical_diet")
    private Boolean isMedicalDiet;

    @Column(name = "warning_note", length = 500)
    private String warningNote;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groom_id", nullable = false)
    private User groom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stall_id")
    private Stall stall;
}
