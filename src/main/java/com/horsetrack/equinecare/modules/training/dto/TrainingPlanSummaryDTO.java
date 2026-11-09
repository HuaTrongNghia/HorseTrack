package com.horsetrack.equinecare.modules.training.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingPlanSummaryDTO {
    private Integer planId;
    private     String planName;
    private String horseName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Double progress;
    private String type;
    private String goal;
    private PlanStaffGuideDTO staffGuide;
    private java.util.List<TrainingPlanPhaseDTO> phases;
    /** SYNTHETIC / DIRT / TURF, null với giáo án cũ chưa gắn đường */
    private String trackType;
    /** HLV hiện nút "Điều chỉnh" (PUT /training-plans/{planId}) */
    private Boolean canAdjust;
    /** HLV hiện nút "Hủy" (PUT /{planId}/cancel) */
    private Boolean canCancel;
    /** Quản lý CLB hiện nút "Hủy cưỡng chế" (PUT /{planId}/force-cancel) */
    private Boolean canForceCancel;
}
