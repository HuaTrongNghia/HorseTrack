package com.horsetrack.service;

import com.horsetrack.dto.PreRaceExamDTO;
import com.horsetrack.dto.SubmitExamResultDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PreRaceExamService {

    private final List<PreRaceExamDTO> mockDatabase = new ArrayList<>();

    public PreRaceExamService() {
        // --- DỮ LIỆU TAB "CHỜ KHÁM" ---
        PreRaceExamDTO p1 = new PreRaceExamDTO();
        p1.setProposalCode("DX-0009"); p1.setProposalDate("03/10/2026");
        p1.setHorseName("Bolt"); p1.setOwnerName("Vũ Thị Mai");
        p1.setTournamentName("Giải Mở rộng Bình Dương 2026"); p1.setRaceDate("25/10/2026"); p1.setDistance("1200 m"); p1.setLocation("Trường đua Đại Nam");
        p1.setTrainerName("Trần Đức Hùng"); p1.setHealthStatus("Theo dõi"); p1.setWarningTag("Còn thuốc trong thời gian phát hiện");
        p1.setTabStatus("CHO_KHAM");

        PreRaceExamDTO p2 = new PreRaceExamDTO();
        p2.setProposalCode("DX-0008"); p2.setProposalDate("02/10/2026");
        p2.setHorseName("Comet"); p2.setOwnerName("Lý Gia Hân");
        p2.setTournamentName("Giải Mở rộng Bình Dương 2026"); p2.setRaceDate("25/10/2026"); p2.setDistance("1200 m"); p2.setLocation("Trường đua Đại Nam");
        p2.setTrainerName("Trần Đức Hùng"); p2.setHealthStatus("Theo dõi"); p2.setWarningTag(null);
        p2.setTabStatus("CHO_KHAM");

        PreRaceExamDTO p3 = new PreRaceExamDTO();
        p3.setProposalCode("DX-0007"); p3.setProposalDate("01/10/2026");
        p3.setHorseName("Thunder"); p3.setOwnerName("Hoàng Thị Lan");
        p3.setTournamentName("Giải đua Đại Việt 2026"); p3.setRaceDate("15/11/2026"); p3.setDistance("1600 m"); p3.setLocation("Trường đua Phú Thọ");
        p3.setTrainerName("Lê Minh Tâm"); p3.setHealthStatus("Khỏe mạnh"); p3.setWarningTag(null);
        p3.setTabStatus("CHO_KHAM");

        // --- DỮ LIỆU TAB "ĐÃ CÓ KẾT QUẢ" ---
        PreRaceExamDTO p4 = new PreRaceExamDTO();
        p4.setProposalCode("DX-0006"); p4.setProposalDate("28/09/2026");
        p4.setHorseName("Flash"); p4.setOwnerName("Đỗ Minh Quân");
        p4.setTournamentName("Giải Mở rộng Bình Dương 2026"); p4.setRaceDate("25/10/2026"); p4.setDistance("1200 m"); p4.setLocation("Trường đua Đại Nam");
        p4.setTrainerName("Trần Đức Hùng"); p4.setTabStatus("DA_CO_KET_QUA");
        p4.setResultStatus("ĐẠT"); p4.setVetNotes("Tim mạch, khớp ổn định. Theo dõi nhịp tim khi bứt tốc."); p4.setNextStepStatus("Chờ chủ ngựa");

        PreRaceExamDTO p5 = new PreRaceExamDTO();
        p5.setProposalCode("DX-0004"); p5.setProposalDate("26/09/2026");
        p5.setHorseName("Bolt"); p5.setOwnerName("Vũ Thị Mai");
        p5.setTournamentName("Giải đua Đại Việt 2026"); p5.setRaceDate("15/11/2026"); p5.setDistance("1600 m"); p5.setLocation("Trường đua Phú Thọ");
        p5.setTrainerName("Trần Đức Hùng"); p5.setTabStatus("DA_CO_KET_QUA");
        p5.setResultStatus("KHÔNG ĐẠT"); p5.setVetNotes("Viêm khớp nhẹ, đang điều trị PD-0012. Không đủ điều kiện thi đấu."); p5.setNextStepStatus("Không đạt khám");

        mockDatabase.add(p1); mockDatabase.add(p2); mockDatabase.add(p3); mockDatabase.add(p4); mockDatabase.add(p5);
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

        // Cập nhật kết quả
        target.setResultStatus(dto.getIsPassed() ? "ĐẠT" : "KHÔNG ĐẠT");
        target.setVetNotes(dto.getVetNotes());
        target.setTabStatus("DA_CO_KET_QUA"); // Đẩy sang tab số 2

        // Xác định bước tiếp theo theo quy trình nghiệp vụ (BR_04)
        if (dto.getIsPassed()) {
            target.setNextStepStatus("Chờ chủ ngựa"); // Pass thì đẩy cho Owner xác nhận
        } else {
            target.setNextStepStatus("Không đạt khám"); // Fail thì kết thúc quy trình
        }
    }
}