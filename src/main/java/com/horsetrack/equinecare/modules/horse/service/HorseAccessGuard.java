package com.horsetrack.equinecare.modules.horse.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Phạm vi dữ liệu của Trainer: chỉ xem và thao tác trên ngựa được Club Manager phân công
 * (Horses.assigned_trainer_id). Các vai trò khác không bị giới hạn ở đây.
 */
@Component
@RequiredArgsConstructor
public class HorseAccessGuard {

    public static final String ROLE_TRAINER = "Trainer";

    private final UserRepository userRepository;

    public boolean isTrainer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> ROLE_TRAINER.equals(a.getAuthority()));
    }

    /** userId của Trainer đang đăng nhập; null nếu người gọi không phải Trainer (không giới hạn). */
    public Integer trainerScopeId() {
        if (!isTrainer()) {
            return null;
        }
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username))
                .getUserId();
    }

    /** Trainer gọi tới ngựa không được phân công cho mình thì 403. */
    public void checkAssigned(Horse horse) {
        Integer trainerId = trainerScopeId();
        if (trainerId == null || horse == null) {
            return;
        }
        if (horse.getAssignedTrainer() == null || !trainerId.equals(horse.getAssignedTrainer().getUserId())) {
            throw new AccessDeniedException("Bạn không được Club Manager phân công phụ trách ngựa " + horse.getHorseName() + ".");
        }
    }
}
