package com.horsetrack.equinecare.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

    private Integer userId;
    private String id; // chuỗi string tiện cho FE (ví dụ: "1", "2")
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String roleName; // Tên role chuẩn backend: "Club Manager", "Trainer", "Veterinarian", "Groom", "Warehouse Manager", "Horse Owner"
    private String role;     // Tên role chuẩn FE: "ClubManager", "HeadTrainer", "Veterinarian", "Groom", "WarehouseManager", "HorseOwner"
    private Boolean isActive;
    private String status;   // "ACTIVE" | "LOCKED"
    private String lockReason;
    private Integer openTasks;    // Số công việc chăm sóc đang mở (dành cho Groom)
    private Integer horsesOwned;  // Số ngựa đang sở hữu (dành cho Horse Owner)
    private String specialization; // Khu chuồng phụ trách (Groom) hoặc phòng khám (BSTY)
    private String licenseNumber;  // Chứng chỉ hành nghề (HLV hoặc BSTY)
    private Integer experienceYears;
    private String lastLogin;
    private LocalDateTime createdAt;
}
