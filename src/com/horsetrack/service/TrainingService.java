package com.horsetrack.service;

import com.horsetrack.dto.TrainingPlanDTO;
import com.horsetrack.dto.TrainingMetricDTO;
import com.horsetrack.dto.EvaluationDTO;
import com.horsetrack.response.ApiResponse;
import org.springframework.stereotype.Service;

@Service
public class TrainingService {

    // FLOW 2.1: Lập giáo án và Kiểm tra Y tế
    public ApiResponse createPlanAndCheckVetlock(TrainingPlanDTO dto) {
        // Giả lập logic check Database: Nếu mã ngựa là H002 thì báo đang bị Vetlock
        if ("H002".equals(dto.getHorseId())) {
            return new ApiResponse(400, "Ngựa đang bị khóa y tế (Vetlock). Không thể lập giáo án!", null);
        }

        // Todo: Lưu giáo án vào bảng TrainingPlan và phân công cho Groom (dto.getGroomId())
        return new ApiResponse(200, "Lập giáo án thành công. Đã gửi lịch cho Groom!", dto);
    }

    // FLOW 2.2: Nhận chỉ số đo lường và Cảnh báo
    public ApiResponse processMetrics(TrainingMetricDTO dto) {
        // Todo: Lưu chỉ số vào bảng TrainingSession

        // Kiểm tra ngưỡng an toàn (Nhịp tim > 170 là nguy hiểm)
        if (dto.getHeartRate() > 170) {
            // Cảnh báo khẩn cấp (có thể gọi hàm gửi Email/SMS cho HLV)
            return new ApiResponse(201, "CẢNH BÁO: Nhịp tim vượt ngưỡng an toàn! Đã gửi thông báo khẩn cho HLV.", dto);
        }

        return new ApiResponse(200, "Đã lưu chỉ số đo lường an toàn.", dto);
    }

    // FLOW 2.2: Đánh giá sau buổi tập
    public ApiResponse submitEvaluation(EvaluationDTO dto) {
        // Todo: Update điểm và nhận xét vào bảng TrainingSession
        return new ApiResponse(200, "Đã lưu đánh giá phong độ thành công!", dto);
    }
}