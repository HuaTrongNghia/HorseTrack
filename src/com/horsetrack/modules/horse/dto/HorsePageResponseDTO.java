package com.horsetrack.equinecare.modules.horse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorsePageResponseDTO {
    private List<HorseResponseDTO> horses;
    private int totalPages;
    private long totalElements;
    private int currentPage;
}
