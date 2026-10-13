package com.horsetrack.controller;

import com.horsetrack.dto.CreateScheduleRequestDTO;
import com.horsetrack.dto.VaccinationRecordDTO;
import com.horsetrack.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/vaccinations")
@CrossOrigin("*")
public class VaccinationController {

    // Lấy danh sách lịch tiêm theo Tab (Đến hạn trong 7 ngày / Sắp tới / Đã thực hiện)
    @GetMapping
    public ResponseEntity<ApiResponse<List<VaccinationRecordDTO>>> getVaccinationRecords(@RequestParam String tab) {

        VaccinationRecordDTO v1 = new VaccinationRecordDTO();
        v1.setRecordCode("VC-036"); v1.setHorseName("Bolt");
        v1.setTypeName("Uốn ván"); v1.setCategory("Vaccine");
        v1.setExpectedDate("04/10/2026"); v1.setCountdown("còn 1 ngày");
        v1.setCycleMonths(12); v1.setStatus("Sắp đến hạn");

        VaccinationRecordDTO v2 = new VaccinationRecordDTO();
        v2.setRecordCode("VC-033"); v2.setHorseName("Arrow");
        v2.setTypeName("Tẩy giun định kỳ"); v2.setCategory("Tẩy giun");
        v2.setExpectedDate("09/10/2026"); v2.setCountdown("còn 6 ngày");
        v2.setCycleMonths(3); v2.setStatus("Sắp đến hạn");

        return ResponseEntity.ok(new ApiResponse<>(200, "Thành công", Arrays.asList(v1, v2)));
    }

    // Ghi nhận đã tiêm một mũi cụ thể (Nút "Ghi nhận đã tiêm")
    @PostMapping("/{recordCode}/execute")
    public ResponseEntity<ApiResponse<String>> executeVaccination(@PathVariable String recordCode) {
        // TODO: Cập nhật DB trạng thái thành "Đã thực hiện" và tự động sinh ra mũi nhắc lại
        return ResponseEntity.ok(new ApiResponse<>(200, "Đã ghi nhận tiêm thành công và tạo lịch nhắc lại!", null));
    }

    // Tạo lịch tiêm mới hàng loạt cho nhiều ngựa (Từ Modal)
    @PostMapping("/schedule")
    public ResponseEntity<ApiResponse<String>> createMassSchedule(@RequestBody CreateScheduleRequestDTO dto) {
        try {
            if (dto.getHorseIds() == null || dto.getHorseIds().isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Vui lòng chọn ít nhất một ngựa!", null));
            }

            // TODO: Vòng lặp for để tạo bản ghi cho từng horseId vào Database
            return ResponseEntity.ok(new ApiResponse<>(200, "Đã tạo lịch " + dto.getMedicineName() + " thành công cho " + dto.getHorseIds().size() + " ngựa.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }
}