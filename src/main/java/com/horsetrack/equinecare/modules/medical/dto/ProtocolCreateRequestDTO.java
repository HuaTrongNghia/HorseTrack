package com.horsetrack.equinecare.modules.medical.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Tạo phác đồ điều trị (hoặc phiên bản mới của phác đồ). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProtocolCreateRequestDTO {

    @NotNull(message = "Phải chọn ngựa (horseId)")
    private Integer horseId;

    // Ca khám liên quan (Vet_Cases), tùy chọn
    private Integer caseId;

    // Bệnh án liên quan (Medical_Records), tùy chọn
    private Integer recordId;

    @NotBlank(message = "Chẩn đoán (diagnosis) là bắt buộc")
    @Size(max = 500, message = "Chẩn đoán không được vượt quá 500 ký tự")
    private String diagnosis;

    // Ngày cần có thuốc; đơn mua về trễ hơn ngày này sẽ báo bác sĩ xem lại (BR_07)
    @FutureOrPresent(message = "Ngày cần thuốc (requiredBy) không được ở trong quá khứ")
    private LocalDate requiredBy;

    @Size(max = 1000, message = "Ghi chú (note) không được vượt quá 1000 ký tự")
    private String note;

    @NotEmpty(message = "Phác đồ phải có ít nhất một giai đoạn (phases)")
    @Valid
    private List<Phase> phases;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Phase {
        @NotBlank(message = "Tên giai đoạn (phaseName) là bắt buộc")
        @Size(max = 100, message = "Tên giai đoạn không được vượt quá 100 ký tự")
        private String phaseName;

        @NotNull(message = "Ngày bắt đầu giai đoạn (startDate) là bắt buộc")
        @FutureOrPresent(message = "Ngày bắt đầu giai đoạn không được ở trong quá khứ")
        private LocalDate startDate;

        @NotNull(message = "Số ngày điều trị (durationDays) là bắt buộc")
        @Min(value = 1, message = "Số ngày điều trị phải từ 1 trở lên")
        private Integer durationDays;

        @Size(max = 255, message = "Mục tiêu (goal) không được vượt quá 255 ký tự")
        private String goal;

        @NotEmpty(message = "Mỗi giai đoạn phải có ít nhất một thuốc/vật tư (items)")
        @Valid
        private List<Item> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @NotNull(message = "Phải chọn thuốc/vật tư (itemId) trong danh mục thuốc")
        private Integer itemId;

        @NotNull(message = "Số lượng (quantity) là bắt buộc")
        @Positive(message = "Số lượng phải lớn hơn 0")
        @Digits(integer = 8, fraction = 2, message = "Số lượng tối đa 2 chữ số thập phân")
        private Double quantity;

        @Size(max = 100, message = "Liều dùng (dosage) không được vượt quá 100 ký tự")
        private String dosage;
    }
}
