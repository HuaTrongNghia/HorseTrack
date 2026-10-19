package com.horsetrack.service;

import com.horsetrack.dto.MedicalCaseDTO;
import com.horsetrack.dto.VetActionRequestDTO;
import com.horsetrack.dto.VetLockRequestDTO;
import com.horsetrack.entity.Horse;
import com.horsetrack.repository.HorseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalService {

    private final HorseRepository horseRepository;
    // Thêm các Repository khác nếu cần (ví dụ: MedicalCaseRepository)

    // 1. Hàm kích hoạt Khóa y tế (VetLock)
    @Transactional
    public void applyVetLock(VetLockRequestDTO dto) throws Exception {
        // Tìm con ngựa trong Database trực tiếp bằng horseId gửi từ Frontend
        Horse horse = horseRepository.findById(dto.getHorseId())
                .orElseThrow(() -> new Exception("Không tìm thấy ngựa với ID: " + dto.getHorseId()));

        // Kiểm tra xem ngựa đã bị khóa chưa
        if (Boolean.TRUE.equals(horse.isLocked())) {
            throw new Exception("Ngựa này hiện đang bị khóa y tế rồi!");
        }

        // Cập nhật trạng thái khóa thành TRUE
        horse.setLocked(true); // Hoặc horse.setIsLocked(true); tùy theo tên biến trong Horse.java
        horseRepository.save(horse);

        // In log ra console để dễ debug
        log.warn("🚨 EMERGENCY VETLOCK: Ngựa ID {} đã bị khóa! Lý do: {}", horse.getId(), dto.getReason());
    }

    // 2. Hàm mở khóa y tế (Hồi phục)
    @Transactional
    public void releaseVetLock(Integer horseId) throws Exception {
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new Exception("Không tìm thấy ngựa!"));

        // Mở khóa bằng cách set lại thành FALSE
        horse.setLocked(false);
        horseRepository.save(horse);
    }

    // 3. BỔ SUNG: Lấy danh sách ca khám theo trạng thái (Phục vụ Hộp thư ca khám)
    public List<MedicalCaseDTO> getInboxCases(String status) {
        // TODO: Viết query thực tế từ MedicalCaseRepository theo status (MOI, DANG_THEO_DOI, DA_DONG)
        // Hiện tại trả về danh sách rỗng để tránh lỗi biên dịch
        return List.of(); 
    }

    // 4. BỔ SUNG: Xử lý hành động của Bác sĩ (Ghi nhận khám, khóa tập, hướng xử lý)
    @Transactional
    public void processVetAction(VetActionRequestDTO dto, Integer vetId) throws Exception {
        // TODO: Tìm ca khám theo dto.getCaseId()
        // Cập nhật kết quả khám (vetNotes), hướng điều trị (treatmentDirection) vào Database
        log.info("BS ID: {} đã xử lý ca khám {}. Hướng xử lý: {}", vetId, dto.getCaseId(), dto.getTreatmentDirection());
    }
}
