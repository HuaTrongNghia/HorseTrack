package com.horsetrack.equinecare.modules.medical.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Lịch tiêm phòng / tẩy giun của ngựa (bảng Vaccinations) */
@Entity
@Table(name = "Vaccinations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vaccination {

    public static final String SCHEDULED = "SCHEDULED";
    public static final String POSTPONED = "POSTPONED";
    public static final String DONE = "DONE";
    public static final String SKIPPED = "SKIPPED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vaccination_id")
    private Integer vaccinationId;

    // Cột tính toán VC-0001, đọc lại sau khi thêm
    @org.hibernate.annotations.Generated
    @Column(name = "vaccination_code", insertable = false, updatable = false)
    private String vaccinationCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    // VACCINE / DEWORM
    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "vaccine_name", nullable = false, length = 100)
    private String vaccineName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private InventoryItem item;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    // Chu kỳ nhắc lại (tháng); ghi nhận đã tiêm thì tạo mũi tiếp theo
    @Column(name = "cycle_months")
    private Integer cycleMonths;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "done_by")
    private User doneBy;

    @Column(name = "lot_no", length = 50)
    private String lotNo;

    @Column(name = "note", length = 500)
    private String note;
}
