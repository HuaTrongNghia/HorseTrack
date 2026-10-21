package com.horsetrack.equinecare.modules.medical.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phác đồ điều trị (bảng Treatment_Protocols).
 * Mỗi lần sửa phác đồ sau khi đã gửi kho là một phiên bản mới (version_no + 1, replaces_protocol_id trỏ bản cũ).
 *
 * Luồng trạng thái (BR_07):
 * WAITING_STOCK_CHECK -> STOCK_AVAILABLE -> VET_CONFIRMED -> IN_TREATMENT -> COMPLETED
 *                     -> STOCK_SHORTAGE -> WAITING_RESTOCK / REQUIRES_VET_REVIEW -> (kiểm kho lại)
 * Bản cũ khi có phiên bản mới: SUPERSEDED. Hủy trước khi cấp thuốc: CANCELLED.
 */
@Entity
@Table(name = "Treatment_Protocols")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TreatmentProtocol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "protocol_id")
    private Integer protocolId;

    // Cột tính toán do DB tự sinh (PD-0001), chỉ đọc
    @Column(name = "protocol_code", insertable = false, updatable = false)
    private String protocolCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vet_id", nullable = false)
    private User vet;

    @Column(name = "case_id")
    private Integer caseId;

    @Column(name = "record_id")
    private Integer recordId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaces_protocol_id")
    private TreatmentProtocol replacesProtocol;

    @Column(name = "diagnosis", nullable = false, length = 500)
    private String diagnosis;

    @Column(name = "state", nullable = false, length = 30)
    private String state;

    @Column(name = "required_by")
    private LocalDate requiredBy;

    @Builder.Default
    @Column(name = "is_delayed", nullable = false)
    private Boolean isDelayed = false;

    @Builder.Default
    @Column(name = "training_locked", nullable = false)
    private Boolean trainingLocked = false;

    @Column(name = "wait_eta")
    private LocalDate waitEta;

    @Column(name = "wait_note", length = 500)
    private String waitNote;

    @Column(name = "wait_decided_at")
    private LocalDateTime waitDecidedAt;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "outcome", length = 1000)
    private String outcome;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Builder.Default
    @OneToMany(mappedBy = "protocol", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("phaseNo ASC")
    private List<ProtocolPhase> phases = new ArrayList<>();

    public void addPhase(ProtocolPhase phase) {
        phases.add(phase);
        phase.setProtocol(this);
    }
}
