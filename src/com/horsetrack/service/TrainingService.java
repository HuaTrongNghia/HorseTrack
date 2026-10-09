package com.horsetrack.service;

import com.horsetrack.dto.EvaluationDTO;
import com.horsetrack.dto.TrainingMetricDTO;
import com.horsetrack.dto.TrainingPlanDTO;
import com.horsetrack.dto.UpdateTaskStatusDTO;
import com.horsetrack.entity.TrainingSession;
import com.horsetrack.repository.TrainingSessionRepository;
import com.horsetrack.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrainingService {

    private final TrainingSessionRepository trainingSessionRepository;

    // FLOW 2.1: Lập giáo án và Kiểm tra Y tế
    public ApiResponse<TrainingPlanDTO> createPlanAndCheckVetlock(TrainingPlanDTO dto) {
        // Giả lập logic check Database: Nếu mã ngựa là H002 thì báo đang bị Vetlock
        if ("H002".equals(dto.getHorseId())) {
            return new ApiResponse<>(400, "Ngựa đang bị khóa y tế (Vetlock). Không thể lập giáo án!", null);
        }

        // TODO: Lưu giáo án vào bảng TrainingPlan và phân công cho Groom (dto.getGroomId())
        return new ApiResponse<>(200, "Lập giáo án thành công. Đã gửi lịch cho Groom!", dto);
    }

    // FLOW 2.2: Nhận chỉ số đo lường và Cảnh báo
    public ApiResponse<TrainingMetricDTO> processMetrics(TrainingMetricDTO dto) {
        // TODO: Lưu chỉ số vào bảng TrainingSession

        // Kiểm tra ngưỡng an toàn (Nhịp tim > 170 là nguy hiểm)
        if (dto.getHeartRate() > 170) {
            return new ApiResponse<>(201, "CẢNH BÁO: Nhịp tim vượt ngưỡng an toàn! Đã gửi thông báo khẩn cho HLV.", dto);
        }

        return new ApiResponse<>(200, "Đã lưu chỉ số đo lường an toàn.", dto);
    }

    // FLOW 2.2: Đánh giá sau buổi tập
    public ApiResponse<EvaluationDTO> submitEvaluation(EvaluationDTO dto) {
        // TODO: Update điểm và nhận xét vào bảng TrainingSession
        return new ApiResponse<>(200, "Đã lưu đánh giá phong độ thành công!", dto);
    }

    // BỔ SUNG: Cập nhật trạng thái buổi tập (COMPLETED / CANCELLED từ Modal màn hình 1)
    @Transactional
    public ApiResponse<TrainingSession> updateTaskStatus(UpdateTaskStatusDTO dto) throws Exception {
        if (dto.getTaskId() == null) {
            throw new Exception("Mã nhiệm vụ (taskId) không được để trống!");
        }

        String status = dto.getStatus() != null ? dto.getStatus().trim().toUpperCase() : "";
        if (!status.equals("COMPLETED") && !status.equals("CANCELLED")) {
            throw new Exception("Trạng thái không hợp lệ. Chỉ chấp nhận COMPLETED hoặc CANCELLED!");
        }

        // Logic nghiệp vụ: Nếu Hủy, bắt buộc phải có Ghi chú/Lý do
        if ("CANCELLED".equals(status)) {
            if (dto.getNotes() == null || dto.getNotes().trim().isEmpty()) {
                throw new Exception("Bắt buộc phải nhập lý do vào ô Ghi chú khi Hủy nhiệm vụ!");
            }
        }

        // Tìm kiếm session trong database
        TrainingSession session = trainingSessionRepository.findById(dto.getTaskId())
                .orElseThrow(() -> new Exception("Không tìm thấy buổi tập với ID: " + dto.getTaskId()));

        // Cập nhật trạng thái và ghi chú
        session.setStatus(status);
        session.setNotes(dto.getNotes() != null ? dto.getNotes().trim() : null);

        TrainingSession savedSession = trainingSessionRepository.save(session);
        return new ApiResponse<>(200, "Cập nhật trạng thái nhiệm vụ thành công!", savedSession);
    }
}