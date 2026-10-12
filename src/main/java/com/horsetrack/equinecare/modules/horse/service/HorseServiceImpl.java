package com.horsetrack.equinecare.modules.horse.service;

import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.dto.*;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.entity.Stall;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.horse.repository.StallRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class HorseServiceImpl implements HorseService {

    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final StallRepository stallRepository;

    public HorseServiceImpl(HorseRepository horseRepository, UserRepository userRepository, StallRepository stallRepository) {
        this.horseRepository = horseRepository;
        this.userRepository = userRepository;
        this.stallRepository = stallRepository;
    }

    private HorseResponseDTO mapToResponseDTO(Horse horse) {
        return HorseResponseDTO.builder()
                .horseId(horse.getHorseId())
                .horseName(horse.getHorseName())
                .dob(horse.getDob())
                .gender(horse.getGender())
                .breed(horse.getBreed())
                .pedigree(horse.getPedigree())
                .weightKg(horse.getWeightKg())
                .healthStatus(horse.getHealthStatus())
                .isLocked(horse.getIsLocked())
                .prizeSharePct(horse.getPrizeSharePct())
                .ownerId(horse.getOwner().getUserId())
                .stallId(horse.getStall() != null ? horse.getStall().getStallId() : null)
                .createdAt(horse.getCreatedAt())
                .build();
    }

    @Override
    public List<HorseResponseDTO> getMyHorses(Integer ownerId) {
        if (!userRepository.existsById(ownerId)) {
            throw new RuntimeException("Owner not found");
        }
        return horseRepository.findByOwnerUserId(ownerId).stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<HorseResponseDTO> getAllHorses(int page, int size, String status) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Horse> horsePage;
        
        if ("PENDING".equalsIgnoreCase(status)) {
            horsePage = horseRepository.findByStallIsNull(pageable);
        } else if ("APPROVED".equalsIgnoreCase(status)) {
            horsePage = horseRepository.findByStallIsNotNull(pageable);
        } else {
            horsePage = horseRepository.findAll(pageable);
        }
        
        return horsePage.map(this::mapToResponseDTO);
    }

    @Override
    public HorseResponseDTO getHorseDetail(Integer horseId) {
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new RuntimeException("ResourceNotFoundException: Horse not found"));
        return mapToResponseDTO(horse);
    }

    @Override
    @Transactional
    public HorseResponseDTO approveHorse(Integer horseId, HorseApprovalRequestDTO request) {
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new RuntimeException("ResourceNotFoundException: Horse not found"));
                
        if (horse.getStall() != null) {
            throw new RuntimeException("BusinessException: Ngựa này đã được duyệt và cấp chuồng");
        }
        
        if (!stallRepository.existsById(request.getStallId()) || stallRepository.existsByIdAndHorseIsNotNull(request.getStallId())) {
            throw new RuntimeException("BusinessException: Chuồng không hợp lệ hoặc đã được sử dụng");
        }
        
        Stall stall = stallRepository.findById(request.getStallId())
                .orElseThrow(() -> new RuntimeException("Stall not found"));
                
        horse.setStall(stall);
        return mapToResponseDTO(horseRepository.save(horse));
    }

    @Override
    @Transactional
    public HorseResponseDTO updateHorse(Integer horseId, HorseUpdateRequestDTO request) {
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new RuntimeException("ResourceNotFoundException: Horse not found"));
                
        if (request.getWeightKg() != null) {
            horse.setWeightKg(request.getWeightKg());
        }
        if (request.getHealthStatus() != null) {
            horse.setHealthStatus(request.getHealthStatus());
        }
        if (request.getIsLocked() != null) {
            horse.setIsLocked(request.getIsLocked());
        }
        
        return mapToResponseDTO(horseRepository.save(horse));
    }

    @Override
    @Transactional
    public void lockHorseTraining(Integer horseId, String reason) {
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new RuntimeException("Horse not found"));
        horse.setIsLocked(true);
        horseRepository.save(horse);
    }

    @Override
    @Transactional
    public HorseResponseDTO registerHorse(HorseRegistrationRequestDTO request) {
        if (horseRepository.existsByHorseNameAndOwner_UserId(request.getHorseName(), request.getOwnerId())) {
            throw new RuntimeException("Horse with this name already exists for the owner");
        }

        com.horsetrack.equinecare.modules.auth.entity.User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new RuntimeException("Owner not found"));

        if (!"Horse Owner".equals(owner.getRoleName())) {
            throw new RuntimeException("User is not a Horse Owner");
        }

        Horse horse = Horse.builder()
                .horseName(request.getHorseName())
                .dob(request.getDob())
                .gender(request.getGender())
                .breed(request.getBreed() != null && !request.getBreed().isEmpty() ? request.getBreed() : "Thoroughbred")
                .pedigree(request.getPedigree())
                .weightKg(request.getWeightKg())
                .healthStatus("Healthy")
                .isLocked(false)
                .prizeSharePct(java.math.BigDecimal.ZERO)
                .createdAt(java.time.LocalDateTime.now())
                .owner(owner)
                .build();

        return mapToResponseDTO(horseRepository.save(horse));
    }
}