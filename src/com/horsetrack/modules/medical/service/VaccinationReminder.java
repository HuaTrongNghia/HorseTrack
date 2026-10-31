package com.horsetrack.equinecare.modules.medical.service;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.medical.entity.Vaccination;
import com.horsetrack.equinecare.modules.medical.repository.VaccinationRepository;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
