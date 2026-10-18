package com.horsetrack.equinecare.modules.care.entity;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Nutrition_Plans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NutritionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Integer planId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(name = "quantity_per_meal", nullable = false)
    private Integer quantityPerMeal; // Số lượng cỏ/cám trừ đi mỗi bữa
}
