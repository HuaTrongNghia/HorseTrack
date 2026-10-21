package com.horsetrack.equinecare.modules.horse.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Một mục kiểm tra của hồ sơ (bảng Registration_Checks), khóa chính ghép (registration_id, check_key). */
@Entity
@Table(name = "Registration_Checks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegistrationCheck {

    @EmbeddedId
    private Id id;

    @MapsId("registrationId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id")
    private HorseRegistration registration;

    @Column(name = "passed")
    private Boolean passed;

    @Column(name = "detail", length = 500)
    private String detail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by")
    private User checkedBy;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Id implements Serializable {
        @Column(name = "registration_id")
        private Integer registrationId;

        @Column(name = "check_key", length = 20)
        private String checkKey; // IDENTITY, HEALTH, OWNERSHIP
    }
}
