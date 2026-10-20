package com.horsetrack.equinecare.modules.inventory.repository;
import com.horsetrack.equinecare.modules.inventory.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Integer> {

    /**
     * Số lượng vật tư sắp về kịp trước ngày targetDate:
     * tổng phần chưa nhận (đặt - đã nhận) của các đơn còn đang chờ hàng.
     */
    @Query(value = "SELECT CAST(COALESCE(SUM(l.quantity_ordered - COALESCE(l.quantity_received, 0)), 0) AS FLOAT) " +
            "FROM Purchase_Order_Lines l JOIN Purchase_Orders o ON o.order_id = l.order_id " +
            "WHERE l.item_id = :itemId AND o.expected_delivery_date <= :targetDate " +
            "AND o.status IN ('ORDERED', 'IN_TRANSIT', 'DELAYED', 'PARTIALLY_RECEIVED')",
            nativeQuery = true)
    Double getIncomingQuantity(@Param("itemId") Integer itemId, @Param("targetDate") LocalDate targetDate);

    // Danh sách đơn, mới nhất trước; tham số null thì bỏ qua
    @Query("SELECT o FROM PurchaseOrder o " +
            "WHERE (:status IS NULL OR o.status = :status) " +
            "AND (:supplierId IS NULL OR o.supplier.supplierId = :supplierId) " +
            "ORDER BY o.orderedAt DESC, o.orderId DESC")
    List<PurchaseOrder> search(@Param("status") String status, @Param("supplierId") Integer supplierId);

    List<PurchaseOrder> findByRequisitionId(Integer requisitionId);
}
