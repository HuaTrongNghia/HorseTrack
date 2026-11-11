package com.horsetrack.equinecare.modules.medical.service;

import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.medical.entity.VetCase;
import com.horsetrack.equinecare.modules.notification.service.AppNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Báo chuông cho mọi Bác sĩ thú y đang hoạt động khi có ca khám mới vào hộp thư (Groom / HLV báo, HLV dừng buổi tập kèm ca khám). */
@Component
@RequiredArgsConstructor
public class VetCaseAlert {

    private static final String VET_CASES_LINK = "/vet/cases";
    private static final Map<String, String> SEVERITY = Map.of(
            "LOW", "Thấp", "MEDIUM", "Trung bình", "HIGH", "Cao", "CRITICAL", "Nguy cấp");

    private final UserRepository userRepository;
    private final AppNotificationService appNotifications;

    public void newCase(VetCase vetCase) {
        String code = vetCase.getCaseCode() != null ? vetCase.getCaseCode() : "#" + vetCase.getCaseId();
        String horse = vetCase.getHorse() != null ? vetCase.getHorse().getHorseName() : "không rõ";
        String level = SEVERITY.getOrDefault(String.valueOf(vetCase.getSeverity()).toUpperCase(), String.valueOf(vetCase.getSeverity()));
        String reporter = vetCase.getReportedBy() != null ? vetCase.getReportedBy().getFullName() : "nhân viên";
        String title = "Ca khám mới " + code + " – ngựa " + horse + " (mức " + level + ")";
        String message = reporter + " báo: " + vetCase.getCaseType() + ". " + vetCase.getDescription()
                + " Mở hộp thư ca khám để tiếp nhận.";
        appNotifications.pushAll(userRepository.findByRoleNameInAndIsActiveTrue(List.of("Veterinarian")),
                "VET_CASE_NEW", title, message, VET_CASES_LINK);
    }
}
