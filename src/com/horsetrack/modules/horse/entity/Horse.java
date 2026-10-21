package com.horsetrack.equinecare.modules.horse.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Horses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Horse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "horse_id")
    private Integer horseId;

    @Column(name = "horse_name", nullable = false, length = 100)
    private String horseName;

    @Column(name = "dob", nullable = false)
    private LocalDate dob;

    @Column(name = "gender", nullable = false, length = 10)
    private String gender;

    @Column(name = "breed", nullable = false, length = 50)
    private String breed;

    @Column(name = "pedigree", length = 255)
    private String pedigree;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "health_status", nullable = false, length = 20)
    private String healthStatus;

    @Column(name = "is_locked", nullable = false)
    private Boolean isLocked;

    @Column(name = "prize_share_pct", precision = 5, scale = 2)
    private BigDecimal prizeSharePct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stall_id", unique = true)
    private Stall stall;

    // Huấn luyện viên được Club Manager phân công phụ trách; Trainer chỉ xem và lập giáo án cho ngựa của mình
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_trainer_id")
    private User assignedTrainer;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Cột tính toán do DB tự sinh (HT-0001), chỉ đọc
    @Column(name = "horse_code", insertable = false, updatable = false)
    private String horseCode;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "height_hands", precision = 4, scale = 1)
    private BigDecimal heightHands;

    @Column(name = "passport_no", length = 50)
    private String passportNo;

    @Column(name = "health_cert_issuer", length = 150)
    private String healthCertIssuer;

    @Column(name = "health_cert_expiry")
    private LocalDate healthCertExpiry;

    // Trạng thái hồ sơ đăng ký mới nhất (Horse_Registrations), chỉ đọc
    @org.hibernate.annotations.Formula("(SELECT TOP 1 r.status FROM Horse_Registrations r WHERE r.horse_id = horse_id ORDER BY r.registration_id DESC)")
    private String latestRegistrationStatus;

    // Trạng thái duyệt hiển thị: PENDING (chờ duyệt), APPROVED (đã duyệt, có chuồng), REJECTED (bị từ chối), CANCELLED
    public static final String APPROVAL_PENDING = "PENDING";
    public static final String APPROVAL_APPROVED = "APPROVED";
    public static final String APPROVAL_REJECTED = "REJECTED";

    public String getApprovalStatus() {
        if (latestRegistrationStatus == null) {
            // Ngựa tạo trước khi có bảng hồ sơ: suy ra từ việc đã được cấp chuồng hay chưa
            return stall != null ? APPROVAL_APPROVED : APPROVAL_PENDING;
        }
        return switch (latestRegistrationStatus) {
            case "PENDING_REVIEW" -> APPROVAL_PENDING;
            case "APPROVED" -> APPROVAL_APPROVED;
            case "REJECTED" -> APPROVAL_REJECTED;
            default -> latestRegistrationStatus;
        };
    }
}
