package com.horsetrack.equinecare.modules.medical.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.medical.dto.CreateScheduleRequestDTO;
import com.horsetrack.equinecare.modules.medical.dto.VaccinationExecuteDTO;
import com.horsetrack.equinecare.modules.medical.dto.VaccinationRecordDTO;
import com.horsetrack.equinecare.modules.medical.service.VaccinationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Lịch tiêm phòng / tẩy giun (bảng Vaccinations) */
@RestController
@RequestMapping({"/api/v1/vaccinations", "/api/vaccinations"})
@RequiredArgsConstructor
public class VaccinationController {

    private final VaccinationService vaccinationService;

    // tab: due (quá hạn / đến hạn trong 7 ngày), upcoming, done, bỏ trống = tất cả; lọc theo quyền xem ngựa
    @GetMapping
    @PreAuthorize("hasAnyAuthority('Veterinarian', 'Club Manager', 'Trainer', 'Groom', 'Horse Owner')")
    @Operation(summary = "Lịch tiêm phòng / tẩy giun", description = "tab: due (quá hạn hoặc đến hạn trong 7 ngày), upcoming, done; bỏ trống là tất cả. Lọc theo horseId và kind. Mỗi vai trò chỉ thấy ngựa mình được xem.")
    public ResponseEntity<ApiResponse<List<VaccinationRecordDTO>>> getVaccinationRecords(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) Integer horseId,
            @RequestParam(required = false) String kind) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy lịch tiêm phòng thành công", vaccinationService.list(tab, horseId, kind)));
    }

    // Bác sĩ tạo lịch tiêm / tẩy giun cho nhiều ngựa
    @PostMapping("/schedule")
    @PreAuthorize("hasAuthority('Veterinarian')")
    @Operation(summary = "Tạo lịch tiêm cho nhiều ngựa", description = "Bác sĩ lập lịch tiêm hoặc tẩy giun cùng một loại cho danh sách ngựa.")
    public ResponseEntity<ApiResponse<List<VaccinationRecordDTO>>> createMassSchedule(@Valid @RequestBody CreateScheduleRequestDTO dto) {
        List<VaccinationRecordDTO> created = vaccinationService.schedule(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã tạo lịch " + dto.getMedicineName().trim() + " cho " + created.size() + " ngựa.", created));
    }

    // Ghi nhận đã tiêm (mã VC-0001 hoặc ID); có chu kỳ thì tự tạo mũi nhắc lại
    @PostMapping("/{recordCode}/execute")
    @PreAuthorize("hasAuthority('Veterinarian')")
    @Operation(summary = "Ghi nhận đã tiêm", description = "recordCode là mã VC-0001 hoặc ID. Có chu kỳ thì hệ thống tự tạo mũi nhắc lại.")
    public ResponseEntity<ApiResponse<List<VaccinationRecordDTO>>> executeVaccination(@PathVariable String recordCode,
                                                                                      @Valid @RequestBody(required = false) VaccinationExecuteDTO dto) {
        List<VaccinationRecordDTO> result = vaccinationService.execute(recordCode, dto);
        return ResponseEntity.ok(ApiResponse.ok(result.size() > 1
                ? "Đã ghi nhận tiêm và tạo lịch nhắc lại ngày " + result.get(1).getExpectedDate() + "."
                : "Đã ghi nhận tiêm.", result));
    }
}
