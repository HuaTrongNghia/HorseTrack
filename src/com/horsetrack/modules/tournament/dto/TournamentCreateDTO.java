package com.horsetrack.equinecare.modules.tournament.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Quản lý CLB tạo / sửa giải đấu (POST /tournaments, PUT /tournaments/{id}) */
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

    // Ngày thi >= hôm nay + 5 ngày; hạn đăng ký <= ngày thi - 3 ngày (kiểm tra ở TournamentService)
    @NotNull(message = "Ngày thi đấu là bắt buộc")
    private LocalDate raceDate;

    @NotNull(message = "Hạn đăng ký là bắt buộc")
    private LocalDate registrationDeadline;

    @NotNull(message = "Cự ly là bắt buộc") @DecimalMin(value = "100", message = "Cự ly tối thiểu 100 m") @DecimalMax(value = "10000", message = "Cự ly tối đa 10000 m")
    private BigDecimal distanceM;

    @NotNull(message = "Lệ phí đăng ký là bắt buộc (nhập 0 nếu miễn phí)") @DecimalMin(value = "0", message = "Lệ phí không được âm")
    private BigDecimal entryFee;

    @Size(max = 500, message = "Điều kiện tham gia tối đa 500 ký tự")
    private String entryConditions;

    @NotNull(message = "Tuổi tối thiểu là bắt buộc") @Min(value = 1, message = "Tuổi tối thiểu từ 1") @Max(value = 40, message = "Tuổi tối đa 40")
    private Integer minAgeYears;

    @NotNull(message = "Tuổi tối đa là bắt buộc") @Min(value = 1, message = "Tuổi tối thiểu từ 1") @Max(value = 40, message = "Tuổi tối đa 40")
    private Integer maxAgeYears;

    @NotNull(message = "Cân nặng tối thiểu là bắt buộc") @DecimalMin(value = "50", message = "Cân nặng tối thiểu 50 kg") @DecimalMax(value = "1500", message = "Cân nặng tối đa 1500 kg")
    private BigDecimal minWeightKg;

    @NotNull(message = "Cân nặng tối đa là bắt buộc") @DecimalMin(value = "50", message = "Cân nặng tối thiểu 50 kg") @DecimalMax(value = "1500", message = "Cân nặng tối đa 1500 kg")
    private BigDecimal maxWeightKg;

    @NotEmpty(message = "Phải chọn ít nhất 1 giống ngựa được tham gia")
    private List<@NotBlank(message = "Tên giống không được trống") @Size(max = 60, message = "Tên giống tối đa 60 ký tự") String> allowedBreeds;

    @NotNull(message = "Số suất (slots) là bắt buộc") @Min(value = 1, message = "Số suất tối thiểu 1") @Max(value = 500, message = "Số suất tối đa 500")
    private Integer slots;

    @Valid @NotEmpty(message = "Phải có bảng thưởng (ít nhất 1 hạng có tiền thưởng > 0)")
    private List<PrizeDTO> prizes;
}
