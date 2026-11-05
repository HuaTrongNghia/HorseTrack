package com.horsetrack.equinecare.modules.training.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Việc Mã phu phải làm và các bước của một loại giáo án. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanStaffGuideDTO {
    private String summary;
    private List<String> steps;
}
