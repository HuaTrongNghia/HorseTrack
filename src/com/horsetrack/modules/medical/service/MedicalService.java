package com.horsetrack.equinecare.modules.medical.service;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.medical.dto.MedicalCaseDTO;
import com.horsetrack.equinecare.modules.medical.dto.VetActionRequestDTO;
import com.horsetrack.equinecare.modules.medical.dto.VetLockRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalService {

    private final HorseRepository horseRepository;

    private boolean isCurrentUserVeterinarian() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "Veterinarian".equals(a.getAuthority()));
    }

    // 1. Hàm kích hoạt Khóa y tế (VetLock)
    @Transactional
    public void applyVetLock(VetLockRequestDTO dto) throws Exception {
        Horse horse = horseRepository.findById(dto.getHorseId())
                .orElseThrow(() -> new Exception("Không tìm thấy ngựa với ID: " + dto.getHorseId()));

        if (Boolean.TRUE.equals(horse.getIsLocked())) {
            throw new Exception("Ngựa này hiện đang bị khóa y tế rồi!");
        }

        horse.setIsLocked(true);

        // Chỉ Bác sĩ thú y mới kết luận chấn thương (INJURED).
        // Trainer/Groom bấm khẩn cấp thì chỉ khóa tập và chuyển sang WATCH để chờ bác sĩ khám.
        boolean isVet = isCurrentUserVeterinarian();
        horse.setHealthStatus(isVet ? "INJURED" : "WATCH");

        horseRepository.save(horse);

        log.warn("EMERGENCY VETLOCK: Ngựa ID {} đã bị khóa bởi {} ({})! Lý do: {}", horse.getHorseId(),
                SecurityContextHolder.getContext().getAuthentication().getName(), isVet ? "INJURED" : "WATCH", dto.getReason());
    }

    // 2. Hàm mở khóa y tế (Hồi phục)
    @Transactional
    public void releaseVetLock(Integer horseId) throws Exception {
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new Exception("Không tìm thấy ngựa!"));

        if (!Boolean.TRUE.equals(horse.getIsLocked())) {
            throw new Exception("Ngựa này hiện không bị khóa y tế, không cần mở khóa!");
        }

        horse.setIsLocked(false);

        // Đặt lại health_status về trạng thái khỏe mạnh hợp lệ của DB (Ví dụ: "Healthy" hoặc "Khỏe mạnh")
        horse.setHealthStatus("HEALTHY");

        horseRepository.save(horse);
    }

    // 3. BỔ SUNG: Lấy danh sách ca khám theo trạng thái (Phục vụ Hộp thư ca khám)
    public List<MedicalCaseDTO> getInboxCases(String status) {
        // Trả về danh sách ca khám phục vụ giao diện Bác sĩ
        return List.of();
    }

    // 4. BỔ SUNG: Xử lý hành động của Bác sĩ (Ghi nhận khám, khóa tập, hướng xử lý)
    @Transactional
    public void processVetAction(VetActionRequestDTO dto, Integer vetId) throws Exception {
        log.info("BS ID: {} đã xử lý ca khám {}. Hướng xử lý: {}", vetId, dto.getCaseId(), dto.getTreatmentDirection());
    }
}
