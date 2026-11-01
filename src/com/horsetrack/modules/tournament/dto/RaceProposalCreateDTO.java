package com.horsetrack.equinecare.modules.tournament.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RaceProposalCreateDTO {

    @NotNull(message = "Phải chọn ngựa")
    private Integer horseId;

    @NotNull(message = "Phải chọn giải đấu")
    private Integer tournamentId;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
