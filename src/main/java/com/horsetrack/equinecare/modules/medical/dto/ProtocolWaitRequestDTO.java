package com.horsetrack.equinecare.modules.medical.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Bác sĩ quyết định chờ hàng về thay vì đổi phác đồ. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProtocolWaitRequestDTO {

    // Bỏ trống thì lấy ngày về muộn nhất mà kho đã báo
    @FutureOrPresent(message = "Ngày chờ hàng (waitEta) không được ở trong quá khứ")
    private LocalDate waitEta;

    @Size(max = 500, message = "Ghi chú (note) không được vượt quá 500 ký tự")
    private String note;
}
