package com.horsetrack.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
@Entity
@Table(name = "TreatmentStages")
public class TreatmentStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "protocol_id", nullable = false)
    private TreatmentProtocol protocol;

    private Integer stageOrder; // Thứ tự giai đoạn (1, 2...)
    
    private String stageName; // Tên giai đoạn (VD: Chống viêm cấp)
    
    private LocalDate startDate; 
    
    private Integer durationDays; // Thời lượng (ngày)
    
    private String goal; // Mục tiêu giai đoạn

    @OneToMany(mappedBy = "stage", cascade = CascadeType.ALL)
    private List<Prescription> prescriptions; // Đơn thuốc trong giai đoạn
}
