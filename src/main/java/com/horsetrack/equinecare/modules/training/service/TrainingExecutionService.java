package com.horsetrack.equinecare.modules.training.service;

import com.horsetrack.equinecare.modules.training.dto.SessionEvaluateRequestDTO;
import com.horsetrack.equinecare.modules.training.dto.SessionExecuteRequestDTO;
import com.horsetrack.equinecare.modules.training.dto.TrainerScheduleDTO;

import java.time.LocalDate;
import java.util.List;

public interface TrainingExecutionService {
    List<TrainerScheduleDTO> getMySchedule(LocalDate startDate, LocalDate endDate);
    void executeSession(Integer sessionId, SessionExecuteRequestDTO request);

    // Groom xác nhận nhận buổi tập được HLV phân công; trả về trạng thái giáo án sau khi xác nhận
    String confirmSession(Integer sessionId);

    // HLV phụ trách dừng buổi tập vì chỉ số sức khỏe vượt ngưỡng; trả về trạng thái mới và mã ca khám
    com.horsetrack.equinecare.modules.training.dto.SessionStopResultDTO stopSession(Integer sessionId, com.horsetrack.equinecare.modules.training.dto.SessionStopRequestDTO request);

    // HLV đánh giá phong độ sau khi buổi tập hoàn thành
    void evaluateSession(Integer sessionId, SessionEvaluateRequestDTO request);
}
