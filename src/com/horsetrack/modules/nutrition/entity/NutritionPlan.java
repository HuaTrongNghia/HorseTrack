package com.horsetrack.equinecare.modules.nutrition.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Nutrition_Plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NutritionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Integer planId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Builder.Default
    @OneToMany(mappedBy = "nutritionPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NutritionPlanDetail> details = new ArrayList<>();

    public void addDetail(NutritionPlanDetail detail) {
        if (details == null) {
            details = new ArrayList<>();
        }
        details.add(detail);
        detail.setNutritionPlan(this);
    }

    public void removeDetail(NutritionPlanDetail detail) {
        if (details != null) {
            details.remove(detail);
            detail.setNutritionPlan(null);
        }
    }

    public Integer getHorseId() {
        return horse != null ? horse.getHorseId() : null;
    }

    public Integer getCreatedById() {
        return createdBy != null ? createdBy.getUserId() : null;
    }
}
