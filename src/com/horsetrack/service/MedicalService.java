package com.horsetrack.service;

import com.horsetrack.dto.VetLockRequestDTO;
import com.horsetrack.entity.Horse;
import com.horsetrack.repository.HorseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalService {

    private final HorseRepository horseRepository;

    // 1. Hàm kích hoạt Khóa y tế (VetLock)
    @Transactional
    public void applyVetLock(VetLockRequestDTO dto) throws Exception {
        // Tìm con ngựa trong Database trực tiếp bằng horseId gửi từ Frontend
        Horse horse = horseRepository.findById(dto.getHorseId())
                .orElseThrow(() -> new Exception("Không tìm thấy ngựa với ID: " + dto.getHorseId()));

        // Kiểm tra xem ngựa đã bị khóa chưa (Tránh lỗi null-pointer nếu DB trả về null)
        if (Boolean.TRUE.equals(horse.isLocked()) || Boolean.TRUE.equals(horse.isLocked())) {
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
        horse.setLocked(false); // Hoặc horse.setIsLocked(false);
        horseRepository.save(horse);
    }
}