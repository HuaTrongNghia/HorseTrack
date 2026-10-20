package com.horsetrack.equinecare.modules.medical.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.medical.dto.CreateScheduleRequestDTO;
import com.horsetrack.equinecare.modules.medical.dto.VaccinationRecordDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/vaccinations", "/api/vaccinations"})
@CrossOrigin("*")
public class VaccinationController {

    // Lấy danh sách lịch tiêm theo Tab (Đến hạn trong 7 ngày / Sắp tới / Đã thực hiện)
    @GetMapping
    public ResponseEntity<ApiResponse<List<VaccinationRecordDTO>>> getVaccinationRecords(@RequestParam(defaultValue = "upcoming") String tab) {
        VaccinationRecordDTO v1 = VaccinationRecordDTO.builder()
                .recordCode("VC-036")
                .horseName("Bolt")
                .typeName("Uốn ván")
                .category("Vaccine")
                .expectedDate("04/10/2026")
                .countdown("còn 1 ngày")
                .cycleMonths(12)
                .status("Sắp đến hạn")
                .build();

        VaccinationRecordDTO v2 = VaccinationRecordDTO.builder()
                .recordCode("VC-033")
                .horseName("Arrow")
                .typeName("Tẩy giun định kỳ")
                .category("Tẩy giun")
                .expectedDate("09/10/2026")
                .countdown("còn 6 ngày")
                .cycleMonths(3)
                .status("Sắp đến hạn")
                .build();

        return ResponseEntity.ok(ApiResponse.ok("Thành công", Arrays.asList(v1, v2)));
    }

    // Ghi nhận đã tiêm một mũi cụ thể (Nút "Ghi nhận đã tiêm")
    @PostMapping("/{recordCode}/execute")
    public ResponseEntity<ApiResponse<String>> executeVaccination(@PathVariable String recordCode) {
        return ResponseEntity.ok(ApiResponse.ok("Đã ghi nhận tiêm thành công và tạo lịch nhắc lại!", null));
    }

    // Tạo lịch tiêm mới hàng loạt cho nhiều ngựa (Từ Modal)
    @PostMapping("/schedule")
    public ResponseEntity<ApiResponse<String>> createMassSchedule(@RequestBody CreateScheduleRequestDTO dto) {
        try {
            if (dto.getHorseIds() == null || dto.getHorseIds().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng chọn ít nhất một ngựa!"));
            }
            return ResponseEntity.ok(ApiResponse.ok("Đã tạo lịch " + dto.getMedicineName() + " thành công cho " + dto.getHorseIds().size() + " ngựa.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
