package com.horsetrack.equinecare.modules.medical.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Một giai đoạn điều trị của phác đồ (bảng Protocol_Phases). Số thứ tự không trùng trong một phác đồ. */
@Entity
@Table(name = "Protocol_Phases")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProtocolPhase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "phase_id")
    private Integer phaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id", nullable = false)
    private TreatmentProtocol protocol;

    @Column(name = "phase_no", nullable = false)
    private Integer phaseNo;

    @Column(name = "phase_name", nullable = false, length = 100)
    private String phaseName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "goal", length = 255)
    private String goal;

    @Builder.Default
    @OneToMany(mappedBy = "phase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("protocolItemId ASC")
    private List<ProtocolItem> items = new ArrayList<>();

    public void addItem(ProtocolItem item) {
        items.add(item);
        item.setPhase(this);
    }
}
