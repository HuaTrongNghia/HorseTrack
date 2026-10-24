package com.horsetrack.equinecare.modules.inventory.repository;
import com.horsetrack.equinecare.modules.inventory.entity.StockTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {

    // Lịch sử cấp phát của một phác đồ / yêu cầu... (ref_type + ref_id), theo thời gian
    java.util.List<StockTransaction> findByRefTypeAndRefIdAndTxTypeOrderByPerformedAtAscTransactionIdAsc(String refType, Integer refId, String txType);

    // Nhật ký kho, mới nhất trước; tham số null thì bỏ qua
    @Query("SELECT t FROM StockTransaction t " +
            "WHERE (:itemId IS NULL OR t.item.itemId = :itemId) " +
            "AND (:txType IS NULL OR t.txType = :txType) " +
            "AND (:fromTime IS NULL OR t.performedAt >= :fromTime) " +
            "AND (:toTime IS NULL OR t.performedAt < :toTime) " +
            "ORDER BY t.performedAt DESC, t.transactionId DESC")
    Page<StockTransaction> search(@Param("itemId") Integer itemId,
                                  @Param("txType") String txType,
                                  @Param("fromTime") LocalDateTime fromTime,
                                  @Param("toTime") LocalDateTime toTime,
                                  Pageable pageable);
}
