package com.horsetrack.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Entity
@Table(name = "TreatmentProtocols")
public class TreatmentProtocol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true)
    private String protocolCode; // Mã phác đồ (VD: PD-0011)

    @ManyToOne
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    private String medicalCaseId; // Mã ca khám liên quan (VD: C-0015)

    private String diagnosis; // Chẩn đoán bệnh
    
    private LocalDate targetDate; // Ngày cần thuốc

    private BigDecimal totalCost; // Tổng chi phí dự kiến
    
    private String status; // Trạng thái (Đang điều trị, Chờ kho cấp phát...)

    @OneToMany(mappedBy = "protocol", cascade = CascadeType.ALL)
    private List<TreatmentStage> stages; // Phác đồ gồm nhiều giai đoạn
}
