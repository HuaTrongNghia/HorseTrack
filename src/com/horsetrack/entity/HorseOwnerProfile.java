package com.horsetrack.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "HorseOwnerProfiles")
@Data
@NoArgsConstructor
public class HorseOwnerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Liên kết 1-1 với bảng User
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Cho phép null và gán luôn giá trị khởi tạo mặc định
    @Column(name = "billing_address", nullable = true)
    private String billingAddress = "Chưa cập nhật";

    @Column(name = "tax_code", nullable = true)
    private String taxCode = "Chưa cập nhật";
}