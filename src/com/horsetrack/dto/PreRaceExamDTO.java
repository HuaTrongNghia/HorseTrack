package com.horsetrack.dto;

import lombok.Data;

@Data
public class PreRaceExamDTO {
    // Thông tin Đề xuất
    private String proposalCode;   // VD: "DX-0009"
    private String proposalDate;   // VD: "03/10/2026"

    // Thông tin Ngựa
    private String horseName;      // VD: "Bolt"
    private String ownerName;      // VD: "Vũ Thị Mai"

    // Thông tin Giải đấu
    private String tournamentName; // VD: "Giải Mở rộng Bình Dương 2026"
    private String raceDate;       // VD: "25/10/2026"
    private String distance;       // VD: "1200 m"
    private String location;       // VD: "Trường đua Đại Nam"

    // Đề xuất bởi
    private String trainerName;    // VD: "Trần Đức Hùng"

    // Tình trạng sức khỏe hiện tại
    private String healthStatus;   // VD: "Theo dõi", "Khỏe mạnh"
    private String warningTag;     // VD: "Còn thuốc trong thời gian phát hiện" (Có thể null)

    // --- CÁC TRƯỜNG DÀNH CHO TAB "ĐÃ CÓ KẾT QUẢ" ---
    private String tabStatus;      // "CHO_KHAM" hoặc "DA_CO_KET_QUA"
    private String resultStatus;   // "ĐẠT" hoặc "KHÔNG ĐẠT"
    private String vetNotes;       // VD: "Tim mạch, khớp ổn định. Theo dõi nhịp tim..."
    private String nextStepStatus; // VD: "Chờ chủ ngựa", "Sẵn sàng gửi BTC", "Không đạt khám"
}