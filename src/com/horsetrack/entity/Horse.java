package com.horsetrack.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "Horses")
@Data
public class Horse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "horse_id") // BẠN PHẢI THÊM DÒNG NÀY VÀ ĐIỀN ĐÚNG TÊN CỘT TRONG SQL SERVER
    private Integer id;

    @Column(name = "horse_name") // Thay "horse_name" bằng tên cột thực tế trong DB
    private String name;

    @Column(name = "is_locked")
    private boolean locked; // Lombok sẽ tự động sinh hàm isLocked()
}