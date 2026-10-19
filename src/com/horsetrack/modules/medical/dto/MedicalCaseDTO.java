package com.horsetrack.dto;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MedicalCaseDTO {
    private String caseId;       // Mã ca (VD: C-0015)
    private Integer horseId;
    private String horseName;    // Tên ngựa 
    private String source;       // Nguồn báo (Dừng phiên, Mã phu báo...)
    private String issueType;    // Loại sự cố (Nghi chấn thương...)
    private String severity;     // Mức độ (Cao, Trung bình, Thấp)
    private String status;       // Trạng thái (Mới, Đang theo dõi, Đã đóng)
    private String description;  // Mô tả chi tiết
    private Integer maxHeartRate;
    private Integer maxSpeed;
    private String vetResult;    
    private LocalDateTime createdAt;
}
