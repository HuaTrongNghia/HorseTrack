package com.horsetrack.equinecare.modules.inventory.entity;
import com.horsetrack.equinecare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phiếu kiểm kê hàng tháng (bảng Stock_Counts). Mỗi tháng chỉ có một phiếu (UQ_SC_month).
 * DRAFT: đang nhập số đếm; DONE: đã chốt và điều chỉnh tồn kho theo số thực tế.
 */
@Entity
@Table(name = "Stock_Counts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockCount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "count_id")
    private Integer countId;

    @Column(name = "count_month", nullable = false, length = 7, columnDefinition = "char(7)")
    private String countMonth; // yyyy-MM

    // Cột tính toán do DB tự sinh (KK-2026-10), chỉ đọc
    @Column(name = "count_code", insertable = false, updatable = false)
    private String countCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counted_by", nullable = false)
    private User countedBy;

    @Column(name = "counted_at", nullable = false)
    private LocalDateTime countedAt;

    @Column(name = "status", nullable = false, length = 10)
    private String status; // DRAFT, DONE

    @Column(name = "note", length = 500)
    private String note;

    @Builder.Default
    @OneToMany(mappedBy = "stockCount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockCountLine> lines = new ArrayList<>();
}
