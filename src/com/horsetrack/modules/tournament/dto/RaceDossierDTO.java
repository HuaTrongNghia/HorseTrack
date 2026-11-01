package com.horsetrack.equinecare.modules.tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Hồ sơ đăng ký thi đấu tổng hợp (chỉ xem trên màn hình, không xuất PDF) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceDossierDTO {
    private RaceProposalDTO proposal;
    private HorseInfo horse;
    private Person owner;
    private Person trainer;
    private List<Vaccine> vaccinations;
    private VetExam vetExam;
    private Payment payment;
    private LocalDateTime generatedAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HorseInfo {
        private Integer horseId;
        private String horseCode;
        private String horseName;
        private LocalDate dob;
        private Integer ageYears;
        private String gender;
        private String breed;
        private String color;
        private String pedigree;
        private BigDecimal weightKg;
        private BigDecimal heightCm;
        private String healthStatus;
        private String stallCode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Person {
        private Integer userId;
        private String fullName;
        private String phone;
        private String email;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Vaccine {
        private String code;
        private String vaccineName;
        private String kind;
        private LocalDate dueDate;
        private LocalDateTime doneAt;
        private String status;
        private String lotNo;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class VetExam {
        private String vetName;
        private Boolean passed;
        private LocalDateTime checkedAt;
        private String note;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Payment {
        private String invoiceCode;
        private BigDecimal amount;
        private String status;
        private LocalDateTime paidAt;
    }
}
