package com.horsetrack.equinecare.modules.assignment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Yêu cầu phân công chưa có hiệu lực: HLV / người chăm phải xác nhận hoặc từ chối.
 * TRAINER_HORSE nhận ngựa, TRAINER_RELEASE bỏ ngựa, GROOM_ZONE nhận hoặc bỏ khu.
 */
@Entity
@Table(name = "Assignment_Requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Integer requestId;

    @Column(name = "request_type", nullable = false, length = 30)
    private String requestType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "target_user_id", nullable = false)
    private Integer targetUserId;

    @Column(name = "horse_id")
    private Integer horseId;

    @Column(name = "previous_user_id")
    private Integer previousUserId;

    @Column(name = "barn_zone", length = 80)
    private String barnZone;

    @Column(name = "previous_zone", length = 80)
    private String previousZone;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "created_by", nullable = false)
    private Integer createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;
}
