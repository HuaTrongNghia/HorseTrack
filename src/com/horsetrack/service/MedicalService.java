package com.horsetrack.service;

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

    // (Tùy chọn) Bạn có thể inject thêm MedicalRecordRepository nếu muốn lưu lịch sử khám

    // 1. Hàm kích hoạt Khóa y tế (VetLock)
    @Transactional
    public void applyVetLock(VetLockRequestDTO dto) throws Exception {
        // Tìm con ngựa trong Database
        Horse horse = horseRepository.findById(dto.getHorseId())
                .orElseThrow(() -> new Exception("Không tìm thấy ngựa với ID: " + dto.getHorseId()));

        // Chặn không cho khóa nếu ngựa đã bị khóa rồi
        if ("VET_LOCKED".equals(horse.getStatus())) {
            throw new Exception("Ngựa này hiện đang bị khóa y tế rồi!");
        }

        // Cập nhật trạng thái ngựa sang VET_LOCKED
        horse.setStatus("VET_LOCKED");
        horseRepository.save(horse);

        log.warn("🚨 EMERGENCY VETLOCK: Ngựa {} (ID: {}) đã bị khóa bởi BS (ID: {}). Lý do: {}",
                horse.getName(), horse.getId(), dto.getVetId(), dto.getReason());

        // TODO: (Mở rộng sau này) Lưu log vào bảng MedicalRecord hoặc gửi Email cho Horse Owner
    }

    // 2. Hàm mở khóa y tế (Hồi phục)
    @Transactional
    public void releaseVetLock(Integer horseId) throws Exception {
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new Exception("Không tìm thấy ngựa!"));

        horse.setStatus("ACTIVE"); // Chuyển lại trạng thái bình thường
        horseRepository.save(horse);
    }
}