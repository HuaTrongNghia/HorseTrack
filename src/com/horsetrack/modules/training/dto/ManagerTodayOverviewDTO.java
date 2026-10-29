package com.horsetrack.equinecare.modules.training.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerTodayOverviewDTO {
    private int horseCount;
    private int newHorsesThisMonth;
    private List<ManagerTodaySessionDTO> sessions;
}
