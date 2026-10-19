package com.horsetrack.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "Medications")
public class Medication {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String code; // Mã thuốc (VD: VT-0010)

    @Column(nullable = false)
    private String name; // Tên thuốc / hoạt chất

    private String category; // Nhóm thuốc (Vaccine, Kháng sinh, Giảm đau...)
    
    private String routeOfAdmin; // Đường dùng (Tiêm IM, Uống, Bôi ngoài da...)
    
    private String referenceDose; // Liều tham khảo
    
    private String dopingDetectionTime; // Thời gian phát hiện khi thi đấu
    
    private BigDecimal price; // Đơn giá
    
    private String unit; // Đơn vị tính (lọ, viên, tuýp, liều...)
    
    private Integer stockAvailable; // Tồn kho khả dụng
}
