package com.horsetrack.equinecare.modules.medical.service;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.medical.entity.Vaccination;
import com.horsetrack.equinecare.modules.medical.repository.VaccinationRepository;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Nhắc lịch tiêm phòng / tẩy giun: tới ngày dự kiến, mỗi bác sĩ thú y đang hoạt động nhận một email tổng hợp
 * "Hôm nay bạn có lịch tiêm phòng / tẩy giun" kèm danh sách ngựa. Chạy hằng ngày (mặc định 07:00).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VaccinationReminder {

    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");


    /** Gửi email nhắc các lịch còn chờ thực hiện của ngày day. Trả về số bác sĩ được gửi. */
    @Transactional(readOnly = true)
    public int remind(LocalDate day) {
        List<Vaccination> due = vaccinationRepository.findByStatusAndDueDateOrderByKindAscVaccinationIdAsc(Vaccination.SCHEDULED, day);
        if (due.isEmpty()) {
            return 0;
        }
        long vaccines = due.stream().filter(v -> !"DEWORM".equals(v.getKind())).count();
        long deworms = due.size() - vaccines;
        String what = vaccines > 0 && deworms > 0 ? "tiêm phòng và tẩy giun" : deworms > 0 ? "tẩy giun" : "tiêm phòng";

        
           
