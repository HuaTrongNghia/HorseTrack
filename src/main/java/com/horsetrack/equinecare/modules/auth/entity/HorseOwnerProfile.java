package com.horsetrack.equinecare.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Horse_Owner_Profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HorseOwnerProfile {
    @Id
    @Column(name = "user_id")
    private Integer userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "tax_code", length = 30)
    private String taxCode;

    @Column(name = "billing_address", length = 255)
    private String billingAddress;

    // Trạng thái hợp đồng dịch vụ: ACTIVE, SUSPENDED (BR_13), REACTIVATION_PENDING (đã trả đủ, chờ Club Manager kích hoạt lại - BR_14)
    @Column(name = "service_status", nullable = false, length = 30)
    @Builder.Default
    private String serviceStatus = "ACTIVE";

    @Column(name = "suspended_since")
    private java.time.LocalDate suspendedSince;

    @Column(name = "suspension_reason", length = 255)
    private String suspensionReason;
}