package com.horsetrack.equinecare.modules.inventory.repository;
import com.horsetrack.equinecare.modules.inventory.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Integer> {
    @Query("SELECT COALESCE(SUM(p.quantity), 0) FROM PurchaseOrder p WHERE p.itemId = :itemId AND p.expectedDeliveryDate <= :targetDate AND p.status = 'SHIPPING'")
    Integer getIncomingQuantity(@Param("itemId") Integer itemId, @Param("targetDate") LocalDate targetDate);
}
