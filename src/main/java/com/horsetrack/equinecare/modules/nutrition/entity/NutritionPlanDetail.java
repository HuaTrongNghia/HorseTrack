package com.horsetrack.equinecare.modules.nutrition.entity;

import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Nutrition_Plan_Details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NutritionPlanDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detail_id")
    private Integer detailId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private NutritionPlan nutritionPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(name = "meal_type", nullable = false, length = 30)
    private String mealType; // MORNING, AFTERNOON, EVENING

    @Column(name = "quantity", nullable = false)
    private double quantity;

    public Integer getInventoryItemId() {
        return inventoryItem != null ? inventoryItem.getItemId() : null;
    }

    public String getInventoryItemName() {
        return inventoryItem != null ? inventoryItem.getItemName() : null;
    }
}