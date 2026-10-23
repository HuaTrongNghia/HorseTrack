package com.horsetrack.equinecare.modules.tournament.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Quản lý CLB thêm giải đua bên ngoài */
@Data
public class TournamentCreateDTO {

    @Size(max = 30, message = "Mã giải tối đa 30 ký tự")
    private String code;

    @NotBlank(message = "Tên giải là bắt buộc") @Size(max = 150, message = "Tên giải tối đa 150 ký tự")
    private String name;

    @NotBlank(message = "Ban tổ chức là bắt buộc") @Size(max = 150, message = "Ban tổ chức tối đa 150 ký tự")
    private String organizer;

    @NotBlank(message = "Địa điểm là bắt buộc") @Size(max = 100, message = "Địa điểm tối đa 100 ký tự")
    private String location;

    @NotNull(message = "Ngày đua là bắt buộc") @FutureOrPresent(message = "Ngày đua không được ở quá khứ")
    private LocalDate raceDate;

    private LocalDate registrationDeadline;

    @NotNull(message = "Cự ly là bắt buộc") @DecimalMin(value = "100", message = "Cự ly tối thiểu 100 m") @DecimalMax(value = "10000", message = "Cự ly tối đa 10000 m")
    private BigDecimal distanceM;

    @DecimalMin(value = "0", message = "Lệ phí không được âm")
    private BigDecimal entryFee;

    @Size(max = 500, message = "Điều kiện tham gia tối đa 500 ký tự")
    private String entryConditions;
}
