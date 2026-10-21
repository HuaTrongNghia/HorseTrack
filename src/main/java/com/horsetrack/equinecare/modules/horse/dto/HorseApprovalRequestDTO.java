package com.horsetrack.equinecare.modules.horse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorseApprovalRequestDTO {

    @NotNull(message = "Phải chọn chuồng (stallId) để cấp cho ngựa")
    private Integer stallId;

    // Checklist kiểm tra (tùy chọn). Nếu gửi thì phải đủ 3 mục và tất cả đều đạt (SRS: chỉ duyệt khi mọi mục đạt).
    @Valid
    private List<RegistrationCheckDTO> checks;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;

    public HorseApprovalRequestDTO(Integer stallId) {
        this.stallId = stallId;
    }
}
