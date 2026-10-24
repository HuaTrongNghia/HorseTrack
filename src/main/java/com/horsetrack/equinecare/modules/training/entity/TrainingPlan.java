package com.horsetrack.equinecare.modules.training.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Training_Plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Integer planId;

    // QA H3: khóa lạc quan - hai thao tác đồng thời trên cùng dòng thì thao tác sau bị từ chối (409) thay vì ghi đè
    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "plan_name", nullable = false, length = 150)
    private String planName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "goal", columnDefinition = "NVARCHAR(500)")
    private String goal;

    // Loại giáo án (ví dụ Duy trì, Tăng sức bền, Chuẩn bị thi đấu, Phục hồi)
    @Column(name = "plan_type", length = 50)
    private String planType;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING (chờ Groom xác nhận / chờ tới ngày bắt đầu), ACTIVE, PAUSED (ngựa bị VetLock), COMPLETED, CANCELLED

    // Lý do tạm dừng giáo án (VD: ngựa bị khóa huấn luyện)
    @Column(name = "paused_reason", length = 500)
    private String pausedReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_trainer_id", nullable = false)
    private User headTrainer;

    @Builder.Default
    @OneToMany(mappedBy = "trainingPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrainingSession> sessions = new ArrayList<>();

    // Giai đoạn của giáo án (Training_Plan_Phases)
    @Builder.Default
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("phaseNo ASC")
    private List<TrainingPlanPhase> phases = new ArrayList<>();

    public void addSession(TrainingSession session) {
        if (sessions == null) {
            sessions = new ArrayList<>();
        }
        sessions.add(session);
        session.setTrainingPlan(this);
    }

    public void removeSession(TrainingSession session) {
        if (sessions != null) {
            sessions.remove(session);
            session.setTrainingPlan(null);
        }
    }
}
