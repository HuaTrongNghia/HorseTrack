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
 *
