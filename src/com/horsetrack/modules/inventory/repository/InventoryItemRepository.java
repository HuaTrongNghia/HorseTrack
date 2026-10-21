package com.horsetrack.equinecare.modules.inventory.repository;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Integer> {

    // Lọc danh mục theo nhóm và từ khóa (tên hoặc mã vật tư); tham số null thì bỏ qua
    @Query("SELECT i FROM InventoryItem i " +
            "WHERE (:category IS NULL OR i.category = :category) " +
            "AND (:keyword IS NULL OR LOWER(i.itemName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "     OR LOWER(i.itemCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY i.itemName")
    List<InventoryItem> search(@Param("category") String category, @Param("keyword") String keyword);

    // Vật tư có khả dụng (đang có - đang giữ) thấp hơn mức an toàn (BR_08)
    @Query("SELECT i FROM InventoryItem i WHERE (i.currentStock - i.heldStock) < i.minSafetyStock ORDER BY i.itemName")
    List<InventoryItem> findBelowSafetyLevel();

    boolean existsByItemNameIgnoreCaseAndCategory(String itemName, String category);

    List<InventoryItem> findByCategoryInOrderByItemNameAsc(java.util.Collection<String> categories);

    List<InventoryItem> findByCategoryNotInOrderByItemNameAsc(java.util.Collection<String> categories);
}
