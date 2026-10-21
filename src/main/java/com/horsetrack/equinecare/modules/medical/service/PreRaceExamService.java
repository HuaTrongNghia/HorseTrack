package com.horsetrack.equinecare.modules.medical.service;

import com.horsetrack.equinecare.modules.medical.dto.PreRaceExamDTO;
import com.horsetrack.equinecare.modules.medical.dto.SubmitExamResultDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PreRaceExamService {

    private final List<PreRaceExamDTO> mockDatabase = new ArrayList<>();

    public PreRaceExamService() {
        // --- DỮ LIỆU TAB "CHỜ KHÁM" ---
        PreRaceExamDTO p1 = PreRaceExamDTO.builder()
                .proposalCode("DX-0009")
                .proposalDate("03/10/2026")
                .horseName("Bolt")
                .ownerName("Vũ Thị Mai")
                .tournamentName("Giải Mở rộng Bình Dương 2026")
                .raceDate("25/10/2026")
                .distance("1200 m")
                .location("Trường đua Đại Nam")
                .trainerName("Trần Đức Hùng")
                .healthStatus("Theo dõi")
                .warningTag("Còn thuốc trong thời gian phát hiện")
                .tabStatus("CHO_KHAM")
                .build();

        PreRaceExamDTO p2 = PreRaceExamDTO.builder()
                .proposalCode("DX-0008")
                .proposalDate("02/10/2026")
                .horseName("Comet")
                .ownerName("Lý Gia Hân")
                .tournamentName("Giải Mở rộng Bình Dương 2026")
                .raceDate("25/10/2026")
                .distance("1200 m")
                .location("Trường đua Đại Nam")
                .trainerName("Trần Đức Hùng")
                .healthStatus("Theo dõi")
                .tabStatus("CHO_KHAM")
                .build();

        PreRaceExamDTO p3 = PreRaceExamDTO.builder()
                .proposalCode("DX-0007")
                .proposalDate("01/10/2026")
                .horseName("Thunder")
                .ownerName("Hoàng Thị Lan")
                .tournamentName("Giải đua Đại Việt 2026")
                .raceDate("15/11/2026")
                .distance("1600 m")
                .location("Trường đua Phú Thọ")
                .trainerName("Lê Minh Tâm")
                .healthStatus("Khỏe mạnh")
                .tabStatus("CHO_KHAM")
                .build();

        // --- DỮ LIỆU TAB "ĐÃ CÓ KẾT QUẢ" ---
        PreRaceExamDTO p4 = PreRaceExamDTO.builder()
                .proposalCode("DX-0006")
                .proposalDate("28/09/2026")
                .horseName("Flash")
                .ownerName("Đỗ Minh Quân")
                .tournamentName("Giải Mở rộng Bình Dương 2026")
                .raceDate("25/10/2026")
                .distance("1200 m")
                .location("Trường đua Đại Nam")
                .trainerName("Trần Đức Hùng")
                .tabStatus("DA_CO_KET_QUA")
                .resultStatus("ĐẠT")
                .vetNotes("Tim mạch, khớp ổn định. Theo dõi nhịp tim khi bứt tốc.")
                .nextStepStatus("Chờ chủ ngựa")
                .build();

        PreRaceExamDTO p5 = PreRaceExamDTO.builder()
                .proposalCode("DX-0004")
                .proposalDate("26/09/2026")
                .horseName("Bolt")
                .ownerName("Vũ Thị Mai")
                .tournamentName("Giải đua Đại Việt 2026")
                .raceDate("15/11/2026")
                .distance("1600 m")
                .location("Trường đua Phú Thọ")
                .trainerName("Trần Đức Hùng")
                .tabStatus("DA_CO_KET_QUA")
                .resultStatus("KHÔNG ĐẠT")
                .vetNotes("Viêm khớp nhẹ, đang điều trị PD-0012. Không đủ điều kiện thi đấu.")
                .nextStepStatus("Không đạt khám")
                .build();

        mockDatabase.add(p1);
        mockDatabase.add(p2);
        mockDatabase.add(p3);
        mockDatabase.add(p4);
        mockDatabase.add(p5);
    }

    // Lấy danh sách theo Tab
    public List<PreRaceExamDTO> getExamsByTab(String tabStatus) {
        return mockDatabase.stream()
                .filter(exam -> exam.getTabStatus().equalsIgnoreCase(tabStatus))
                .collect(Collectors.toList());
    }

    // Xử lý khi Bác sĩ submit form kết quả
    public void submitExamResult(String proposalCode, SubmitExamResultDTO dto) throws Exception {
        PreRaceExamDTO target = mockDatabase.stream()
                .filter(e -> e.getProposalCode().equals(proposalCode) && e.getTabStatus().equals("CHO_KHAM"))
                .findFirst()
                .orElseThrow(() -> new Exception("Không tìm thấy mã đề xuất hoặc đề xuất đã được khám!"));

        target.setResultStatus(Boolean.TRUE.equals(dto.getIsPassed()) ? "ĐẠT" : "KHÔNG ĐẠT");
        target.setVetNotes(dto.getVetNotes());
        target.setTabStatus("DA_CO_KET_QUA");

        if (Boolean.TRUE.equals(dto.getIsPassed())) {
            target.setNextStepStatus("Chờ chủ ngựa");
        } else {
            target.setNextStepStatus("Không đạt khám");
        }
    }
}
