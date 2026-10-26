package com.horsetrack.equinecare.modules.inventory.dto;

import lombok.Data;

import java.util.List;

/** Kho chọn giai đoạn cần cấp. Bỏ trống thì cấp mọi giai đoạn còn lại. */
@Data
public class DispenseRequestDTO {
    private List<Integer> phaseNos;
}
